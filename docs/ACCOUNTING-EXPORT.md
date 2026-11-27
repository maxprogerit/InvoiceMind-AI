# Accounting export (Xero, QuickBooks Online)

*Export → Accounting* hands every **approved, not yet exported** invoice to accounting as a bill-import CSV, marks
those invoices with the export batch (`invoices.exported_at`, `export_batch_id`) and never offers them again. The file of
any earlier batch can be downloaded again (`GET /api/v1/export/accounting/batches/{id}/file`). Accountants and admins
export; approvers do not. Exported invoices leave the *Overdue* queue and the reminder e-mails: they are paid from the
accounting system now.

Options chosen on the page: the **account** used for invoices without an expense category (an invoice's category is
used as its account, so name categories after your chart of accounts), the **tax code** (Xero tax rate name / QuickBooks
tax code exactly as set up there; leave empty when you do not use one) and the **date format** (pick the same one in the
importer).

Every text cell is neutralised against spreadsheet formula injection (a leading `=`, `+`, `-`, `@` gets a `'`).

## Xero: bills import

Columns, in this order (asterisk = required by Xero):

```
*ContactName, EmailAddress, POAddressLine1, POAddressLine2, POAddressLine3, POAddressLine4, POCity, PORegion,
POPostalCode, POCountry, *InvoiceNumber, *InvoiceDate, *DueDate, Total, InventoryItemCode, Description, *Quantity,
*UnitAmount, *AccountCode, *TaxType, TaxAmount, TrackingName1, TrackingOption1, TrackingName2, TrackingOption2, Currency
```

One row per line item; rows with the same `*InvoiceNumber` and `*ContactName` form one bill. The tax amount is given
once per bill (first line). A credit note has negative unit amounts and is imported as a credit note. Without a
printed due date, the invoice date is used (Xero requires one). Import in Xero: *Business → Bills to pay → Import*.

**Source and confidence.** Xero's official article is *Import bills and credit notes*
(https://central.xero.com/s/article/Import-bills-and-credit-notes-US). `central.xero.com` could not be reached from
the environment this was built in, so these headers are the ones of Xero's published bill/invoice CSV template as
known from its documentation, **not checked against a template downloaded today**. Before the first real import,
download the template from your Xero organisation and compare the header row; Xero also checks the headers itself
and refuses a file that does not match.

## QuickBooks Online: import bills

Columns:

```
Bill No, Supplier, Bill Date, Due Date, Memo, Account, Line Description, Line Amount, Line Tax Code, Line Tax Amount, Currency
```

Intuit lists as mandatory: *Bill no., Supplier, Bill Date, Due Date, Account, Line Amount* and *Line Tax Code* (the
tax columns only when sales tax / VAT is turned on; leave the tax code empty otherwise). Each line repeats bill number,
supplier and date, as Intuit's example file does. QuickBooks maps the file's headings to its fields during the import,
so `Memo`, `Line Description`, `Line Tax Amount` and `Currency` are offered for mapping rather than required. Intuit
recommends at most 100 bills per import. **Credit notes are not imported as bills by QuickBooks**, so they are left out
of the file and listed on the page (enter them as supplier credits). To keep the bill numbers, turn on custom
transaction numbers in QuickBooks. Import: *Settings → Import data → Bills*.

**Source.** Intuit, *Import your bills in QuickBooks Online*
(https://quickbooks.intuit.com/learn-support/en-us/help-articles/importing-your-bills/00/261324 and the regional
versions, e.g. https://quickbooks.intuit.com/learn-support/en-global/help-article/import-transactions/import-bills-quickbooks-online/L4Q6QWsRw_ROW_en).
The mandatory list and the multi-line rule come from that article (read through search results; the site itself
could not be fetched from the build environment). The exact header text of the optional columns, notably whether
the example file says `Currency` or `Currency Code`, is not confirmed; it does not matter for the import because
the columns are mapped by hand.

## Webhook `invoice.approved`

For systems that should react at once instead of importing files: *Integrations* registers an HTTPS endpoint; every
approval (by people or by the approval rules) is sent as `invoice.approved`, signed with HMAC-SHA256
(`X-InvoiceMind-Signature: t=<unix seconds>,v1=<hex of HMAC(secret, "<t>.<raw body>")>`), retried with exponential
backoff, and logged per delivery (status, response code, attempts) with a manual retry. See the README section
*Webhooks: verifying a request*.
