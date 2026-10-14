package com.invoicemind.ai.export;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExportSafetyTest {
  @Test
  void neutralisesSpreadsheetFormulas() {
    assertThat(ExportService.safe("=HYPERLINK(\"http://evil\",\"x\")")).startsWith("'=");
    assertThat(ExportService.safe("+1+1")).startsWith("'+");
    assertThat(ExportService.safe("-2+3")).startsWith("'-");
    assertThat(ExportService.safe("@SUM(A1)")).startsWith("'@");
    assertThat(ExportService.safe("\tcmd")).startsWith("'\t");
  }

  @Test
  void leavesNormalTextAlone() {
    assertThat(ExportService.safe("ACME GmbH")).isEqualTo("ACME GmbH");
    assertThat(ExportService.safe("")).isEmpty();
    assertThat(ExportService.safe(null)).isNull();
  }
}
