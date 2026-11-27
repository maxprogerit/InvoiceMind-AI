package com.invoicemind.ai.ai;

import com.invoicemind.ai.ai.AiDtos.EmbedResponse;
import com.invoicemind.ai.ai.AiDtos.ExplainRequest;
import com.invoicemind.ai.ai.AiDtos.ExplainResponse;
import com.invoicemind.ai.ai.AiDtos.InvoiceResult;
import java.util.List;
import com.invoicemind.ai.ai.AiDtos.OcrResponse;

/** The internal extract-service. Every response is untrusted input. */
public interface AiClient {
  /** File -> text -> model -> first-pass validation, in one call (POST /invoices). */
  InvoiceResult processInvoice(byte[] data, String filename);

  /** Text only (used for knowledge-base PDFs). */
  OcrResponse ocr(byte[] data, String filename);

  /** One vector per text, in order. */
  EmbedResponse embed(List<String> texts);

  ExplainResponse explain(ExplainRequest request);
}
