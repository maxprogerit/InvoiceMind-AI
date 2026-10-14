package com.invoicemind.ai.ai;

import com.invoicemind.ai.ai.AiDtos.ExtractResponse;
import com.invoicemind.ai.ai.AiDtos.OcrResponse;

public interface AiClient {
  OcrResponse ocr(byte[] data, String filename);

  ExtractResponse extract(String text);
}
