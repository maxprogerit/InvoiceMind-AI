package com.invoicemind.ai.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits a rules document into overlapping passages for embedding: paragraphs are kept together where
 * possible, long paragraphs are cut at sentence (then word) boundaries, and each chunk starts with the
 * tail of the previous one so a rule split across a boundary is still retrievable.
 */
public final class Chunker {
  public static final int MAX_CHUNKS = 400;
  private static final Pattern PARAGRAPH = Pattern.compile("\\n\\s*\\n");
  private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?])\\s+");

  private Chunker() {}

  public static List<String> split(String text, int maxChars, int overlap) {
    if (maxChars < 100 || overlap < 0 || overlap >= maxChars / 2) throw new IllegalArgumentException("bad chunk sizes");
    String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').replace('\u0000', ' ').strip();
    if (normalized.isEmpty()) return List.of();

    List<String> pieces = new ArrayList<>();
    for (String paragraph : PARAGRAPH.split(normalized)) {
      String p = paragraph.strip().replaceAll("[ \\t]+", " ");
      if (p.isEmpty()) continue;
      if (p.length() <= maxChars) {
        pieces.add(p);
      } else {
        for (String sentence : SENTENCE.split(p)) addSentence(pieces, sentence, maxChars);
      }
    }

    List<String> chunks = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String piece : pieces) {
      if (current.length() > 0 && current.length() + 1 + piece.length() > maxChars) {
        chunks.add(current.toString());
        String tail = tail(current.toString(), overlap);
        current = new StringBuilder(tail);
      }
      if (current.length() > 0) current.append('\n');
      current.append(piece);
    }
    if (current.length() > 0 && (chunks.isEmpty() || !chunks.get(chunks.size() - 1).endsWith(current.toString()))) {
      chunks.add(current.toString());
    }
    return chunks.size() > MAX_CHUNKS ? chunks.subList(0, MAX_CHUNKS) : chunks;
  }

  /** A sentence longer than a chunk (or text without punctuation) is cut at word boundaries, hard-cut as a last resort. */
  private static void addSentence(List<String> out, String sentence, int max) {
    String s = sentence.strip();
    while (s.length() > max) {
      int cut = s.lastIndexOf(' ', max);
      if (cut < max / 2) cut = max;
      out.add(s.substring(0, cut).strip());
      s = s.substring(cut).strip();
    }
    if (!s.isEmpty()) out.add(s);
  }

  private static String tail(String text, int overlap) {
    if (overlap == 0 || text.length() <= overlap) return overlap == 0 ? "" : text;
    String t = text.substring(text.length() - overlap);
    int space = t.indexOf(' ');
    return space >= 0 && space < t.length() - 1 ? t.substring(space + 1) : t;
  }
}
