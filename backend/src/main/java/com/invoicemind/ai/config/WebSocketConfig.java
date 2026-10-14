package com.invoicemind.ai.config;

import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.security.JwtService;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Live status channel. Authentication happens in the STOMP CONNECT frame (Bearer JWT header); the
 * subscription whitelist below stops a user from listening to anything but their own queues and,
 * for reviewers, their organisation's reviewer topic.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
  public static final String REVIEWERS_TOPIC = "/topic/org/%s/reviewers";

  private final JwtService jwtService;
  private final AppProperties props;

  /** Principal whose name is the user id, which is what convertAndSendToUser routes on. */
  public record StompUser(AuthPrincipal auth) implements Principal {
    @Override
    public String getName() { return auth.userId().toString(); }
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws/live").setAllowedOrigins(props.cors().allowedOrigins().toArray(String[]::new));
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic", "/queue");
    registry.setUserDestinationPrefix("/user");
    registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(new ChannelInterceptor() {
      @Override
      public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;
        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
          List<String> header = accessor.getNativeHeader("Authorization");
          String bearer = header == null || header.isEmpty() ? "" : header.get(0);
          AuthPrincipal principal = bearer.startsWith("Bearer ")
              ? jwtService.parse(bearer.substring(7)).orElse(null) : null;
          if (principal == null) throw new MessagingException("Unauthorized");
          accessor.setUser(new StompUser(principal));
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
          authorizeSubscription(accessor);
        } else if (StompCommand.SEND.equals(command)) {
          throw new MessagingException("Clients may not publish messages");
        }
        return message;
      }
    });
  }

  private void authorizeSubscription(StompHeaderAccessor accessor) {
    if (!(accessor.getUser() instanceof StompUser user)) throw new MessagingException("Unauthorized");
    String destination = accessor.getDestination();
    if ("/user/queue/invoices".equals(destination) || "/user/queue/notifications".equals(destination)) return;
    AuthPrincipal p = user.auth();
    if (destination != null && destination.equals(REVIEWERS_TOPIC.formatted(p.organizationId())) && p.role().isReviewer()) return;
    throw new MessagingException("Forbidden destination");
  }
}
