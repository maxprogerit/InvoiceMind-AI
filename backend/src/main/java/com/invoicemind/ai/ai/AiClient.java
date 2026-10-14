package com.invoicemind.ai.ai;

import com.invoicemind.ai.ai.AiDtos.EmbedResponse;
import com.invoicemind.ai.ai.AiDtos.ExplainRequest;
import com.invoicemind.ai.ai.AiDtos.ExplainResponse;
import com.invoicemind.ai.ai.AiDtos.ExtractResponse;
import java.util.List;
import com.invoicemind.ai.ai.AiDtos.OcrResponse;

public interface AiClient {
  OcrResponse ocr(byte[] data, String filename);

  ExtractResponse extract(String text);

  /** One vector per text, in order. */
  EmbedResponse embed(List<String> texts);

  ExplainResponse explain(ExplainRequest request);
}
