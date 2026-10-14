package com.invoicemind.ai.webhook;

import com.invoicemind.ai.common.ApiException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * SSRF guard for webhook targets. The URL comes from an admin but is requested by the server, so by default only
 * public addresses are allowed: no loopback, private ranges, link-local (cloud metadata at 169.254.169.254),
 * carrier-grade NAT, multicast or IPv6 unique-local. Every address the host name resolves to must pass.
 * Redirects are never followed, so a public URL cannot bounce the request to an internal one.
 */
public final class UrlGuard {
  private UrlGuard() {}

  public static URI check(String raw, boolean allowPrivate) {
    URI uri;
    try {
      uri = URI.create(raw == null ? "" : raw.trim());
    } catch (IllegalArgumentException e) {
      throw invalid("The URL is not valid");
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("https") && !scheme.equals("http")) throw invalid("Only http and https URLs are allowed");
    if (uri.getHost() == null || uri.getHost().isBlank()) throw invalid("The URL needs a host name");
    if (uri.getUserInfo() != null) throw invalid("Credentials inside the URL are not allowed; use the signature instead");
    if (!allowPrivate) {
      InetAddress[] addresses;
      try {
        addresses = InetAddress.getAllByName(uri.getHost());
      } catch (UnknownHostException e) {
        throw invalid("The host name '" + uri.getHost() + "' cannot be resolved");
      }
      for (InetAddress a : addresses) {
        if (isInternal(a)) throw invalid("The URL points to a private or internal address, which is not allowed");
      }
    }
    return uri;
  }

  static boolean isInternal(InetAddress a) {
    if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
        || a.isMulticastAddress()) return true;
    byte[] b = a.getAddress();
    if (a instanceof Inet4Address) {
      int first = b[0] & 0xff, second = b[1] & 0xff;
      return first == 0 || (first == 100 && second >= 64 && second <= 127)  // 0.0.0.0/8, carrier-grade NAT 100.64/10
          || first >= 240;                                                   // reserved / broadcast
    }
    if (a instanceof Inet6Address) {
      return (b[0] & 0xfe) == 0xfc; // fc00::/7 unique-local (IPv4-mapped addresses are already unwrapped by the JDK)
    }
    return false;
  }

  private static ApiException invalid(String message) {
    return ApiException.badRequest("invalid_webhook_url", message);
  }
}
