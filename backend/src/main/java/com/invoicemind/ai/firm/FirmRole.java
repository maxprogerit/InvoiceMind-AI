package com.invoicemind.ai.firm;

/** What a person is in the firm itself, independent of the role they hold inside any one client company. */
public enum FirmRole {
  /** Sees and manages every client company of the firm, and the firm's team. */
  FIRM_ADMIN,
  /** Works only in the client companies they have been given access to. */
  ACCOUNTANT
}
