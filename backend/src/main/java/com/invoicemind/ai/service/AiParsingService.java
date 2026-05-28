package com.invoicemind.ai.service;

import com.invoicemind.ai.dto.InvoiceDtos.OcrResultResponse;

public interface AiParsingService {
  OcrResultResponse parseDocument(String fileName);
  void emitLiveProcessing(Long documentId, String traceId);
}
