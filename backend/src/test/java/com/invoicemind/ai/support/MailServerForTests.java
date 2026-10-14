package com.invoicemind.ai.support;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;

/** A real IMAP/SMTP server (GreenMail) for the e-mail ingestion tests. */
public final class MailServerForTests {
  public static final String ADDRESS = "invoices@example.com";
  public static final String LOGIN = "invoices-user";
  public static final String PASSWORD = "mailbox-secret";

  private static final GreenMail SERVER = start();

  private MailServerForTests() {}

  private static GreenMail start() {
    GreenMail g = new GreenMail(new ServerSetup[]{new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAP)});
    g.setUser(ADDRESS, LOGIN, PASSWORD);
    g.start();
    return g;
  }

  public static GreenMail server() { return SERVER; }

  public static int imapPort() { return SERVER.getImap().getPort(); }
}
