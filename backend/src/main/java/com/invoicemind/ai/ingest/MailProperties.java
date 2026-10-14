package com.invoicemind.ai.ingest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * E-mail ingestion: one IMAP mailbox is polled; a message to {@code <local>+<token>@<domain>} (the address in
 * {@code address} with the organisation's token as plus-tag) lands in that organisation. Disabled unless
 * {@code enabled} is true and the mailbox is configured.
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
    @DefaultValue("false") boolean enabled,
    /** The mailbox address people send to, e.g. invoices@example.com. Per-organisation addresses are derived from it. */
    @DefaultValue("") String address,
    @DefaultValue("") String host,
    @DefaultValue("993") int port,
    @DefaultValue("") String username,
    @DefaultValue("") String password,
    @DefaultValue("true") boolean ssl,
    @DefaultValue("INBOX") String folder,
    @DefaultValue("60") int pollSeconds,
    /** Require the receiving mail server's Authentication-Results to show dkim=pass or spf=pass (sender not forged). */
    @DefaultValue("false") boolean requireAuthResults,
    @DefaultValue("20") int maxMessagesPerRun,
    @DefaultValue("5") int maxAttachments,
    @DefaultValue("30000000") long maxMessageBytes) {

  public boolean configured() {
    return enabled && !host.isBlank() && !username.isBlank() && address.contains("@");
  }
}
