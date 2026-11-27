package com.invoicemind.ai.ingest;

/** Who may send invoices to an organisation's e-mail address. */
public enum IngestPolicy {
  /** Any sender, typically vendors mailing their invoices; the sender is stored on the invoice. */
  ANYONE,
  /** Only enabled users of the organisation (stricter; mail from anyone else is rejected and logged). */
  USERS
}
