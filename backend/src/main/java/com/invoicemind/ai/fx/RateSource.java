package com.invoicemind.ai.fx;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Where reference rates come from. Rates are quoted per 1 EUR (the ECB convention). */
public interface RateSource {
  /** Business-day rates between the two dates (inclusive), keyed by date then currency code. */
  Map<LocalDate, Map<String, BigDecimal>> fetch(LocalDate from, LocalDate to) throws IOException;
}
