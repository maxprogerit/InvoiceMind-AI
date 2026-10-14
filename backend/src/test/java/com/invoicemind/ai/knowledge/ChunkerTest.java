package com.invoicemind.ai.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {
  @Test
  void shortTextIsASingleChunk() {
    assertThat(Chunker.split("Invoices above 10,000 EUR need two approvals.", 900, 150))
        .containsExactly("Invoices above 10,000 EUR need two approvals.");
  }

  @Test
  void emptyAndBlankInputYieldNothing() {
    assertThat(Chunker.split("", 900, 150)).isEmpty();
    assertThat(Chunker.split("  \n\n \t ", 900, 150)).isEmpty();
    assertThat(Chunker.split(null, 900, 150)).isEmpty();
  }

  @Test
  void paragraphsAreGroupedAndChunksStayWithinTheLimit() {
    String paragraph = "Rule: every supplier invoice must reference a purchase order number. ".repeat(6).strip();
    List<String> chunks = Chunker.split(String.join("\n\n", java.util.Collections.nCopies(10, paragraph)), 900, 150);
    assertThat(chunks.size()).isBetween(2, 10);
    // a chunk may be at most max + overlap (the overlap seeds the next chunk)
    assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(900 + 150 + 2));
  }

  @Test
  void consecutiveChunksOverlapSoARuleSplitAtABoundaryIsNotLost() {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < 60; i++) text.append("Sentence number ").append(i).append(" explains rule ").append(i).append(" in detail. ");
    List<String> chunks = Chunker.split(text.toString(), 400, 100);
    assertThat(chunks.size()).isGreaterThan(3);
    for (int i = 1; i < chunks.size(); i++) {
      String prevTail = chunks.get(i - 1).substring(Math.max(0, chunks.get(i - 1).length() - 100));
      String firstWord = chunks.get(i).split("\\s+")[0];
      assertThat(prevTail).contains(firstWord);
    }
  }

  @Test
  void textWithoutSpacesIsHardCutInsteadOfLooping() {
    List<String> chunks = Chunker.split("x".repeat(5000), 900, 150);
    assertThat(chunks).isNotEmpty();
    assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(900 + 150 + 2));
    assertThat(String.join("", chunks).replace("\n", "").length()).isGreaterThanOrEqualTo(5000);
  }

  @Test
  void unicodeAndWindowsLineEndingsSurvive() {
    List<String> chunks = Chunker.split("Правило первое: счёт нужен.\r\n\r\nRegel zwei: Überweisung nur nach Freigabe.", 900, 150);
    assertThat(String.join("\n", chunks)).contains("Правило первое").contains("Überweisung").doesNotContain("\r");
  }

  @Test
  void numberOfChunksIsCapped() {
    String huge = "word ".repeat(200_000);
    assertThat(Chunker.split(huge, 200, 20).size()).isLessThanOrEqualTo(Chunker.MAX_CHUNKS);
  }

  @Test
  void rejectsNonsenseSizes() {
    assertThatThrownBy(() -> Chunker.split("abc", 50, 10)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Chunker.split("abc", 900, 600)).isInstanceOf(IllegalArgumentException.class);
  }
}
