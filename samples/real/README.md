# Real invoices for the evaluation

Put real invoices here, **with personal and sensitive data blacked out** (names of private persons, addresses,
IBANs, tax ids you do not want to publish). Blacking out must change the pixels, not add a box on top of a text
layer: print the redacted PDF to a new PDF or scan it, then check that the hidden text cannot be selected.

```
samples/real/invoices/r01-hosting.pdf
samples/real/ground_truth/r01-hosting.json
```

Ground truth is what is printed on the page (after redaction), money as decimal strings:

```json
{
  "meta": {"file": "r01-hosting.pdf", "synthetic": false, "tags": ["text-pdf"], "source": "own supplier, 2026"},
  "expected": {
    "document_type": "invoice",
    "vendor": "Example Hosting d.o.o.",
    "invoice_number": "2026-0412",
    "invoice_date": "2026-03-14",
    "due_date": "2026-03-29",
    "currency": "RSD",
    "subtotal": "10000.00",
    "tax": "2000.00",
    "total": "12000.00",
    "line_items": [{"description": "VPS hosting March", "quantity": 1, "unit_price": "10000.00", "total": "10000.00"}]
  }
}
```

Useful tags (they group the report): `text-pdf`, `scan`, `scanned-pdf`, `multi-page`, `multi-tax`, `discount`,
`credit-note`, `decimal-comma`. A blacked-out field is `null` in the ground truth. `eval/run_eval.py` picks the
folder up automatically and reports real documents separately from the synthetic ones.
