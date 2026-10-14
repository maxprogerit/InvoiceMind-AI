package com.invoicemind.ai.knowledge;

import com.invoicemind.ai.ai.AiClient;
import com.invoicemind.ai.ai.AiDtos.EmbedResponse;
import com.invoicemind.ai.ai.AiDtos.ExplainRequest;
import com.invoicemind.ai.ai.AiDtos.ExplainResponse;
import com.invoicemind.ai.ai.AiDtos.Passage;
import com.invoicemind.ai.audit.AuditService;
import com.invoicemind.ai.common.ApiException;
import com.invoicemind.ai.invoice.InvoiceDtos.InvoiceDetail;
import com.invoicemind.ai.invoice.InvoiceService;
import com.invoicemind.ai.knowledge.KnowledgeDtos.AnswerDto;
import com.invoicemind.ai.knowledge.KnowledgeDtos.DocumentDto;
import com.invoicemind.ai.knowledge.KnowledgeDtos.Source;
import com.invoicemind.ai.knowledge.KnowledgeRepository.DocumentRow;
import com.invoicemind.ai.knowledge.KnowledgeRepository.Hit;
import com.invoicemind.ai.ratelimit.RateLimiter;
import com.invoicemind.ai.security.AuthPrincipal;
import com.invoicemind.ai.storage.FileValidator;
import com.invoicemind.ai.user.Role;
import com.invoicemind.ai.user.UserEntity;
import com.invoicemind.ai.user.UserRepository;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Retrieval-augmented explanations. Documents are chunked, embedded by the ai-service and stored with
 * pgvector; a question retrieves the organisation's nearest passages and the model answers from them
 * with citations. Passages are untrusted data (the ai-service prompt says so and never gives the model tools).
 */
@Slf4j
@Service
public class KnowledgeService {
  static final int CHUNK_CHARS = 900;
  static final int CHUNK_OVERLAP = 150;
  static final int EMBED_BATCH = 32;
  static final int TOP_K = 4;
  static final int MAX_TEXT_BYTES = 2 * 1024 * 1024;
  static final String DEFAULT_QUESTION = "Why was this invoice flagged, and what do our rules say about it?";

  private final KnowledgeRepository repository;
  private final AiClient ai;
  private final AuditService audit;
  private final RateLimiter rateLimiter;
  private final InvoiceService invoices;
  private final FileValidator fileValidator;
  private final UserRepository users;
  private final TransactionTemplate tx;

  public KnowledgeService(KnowledgeRepository repository, AiClient ai, AuditService audit, RateLimiter rateLimiter,
                          InvoiceService invoices, FileValidator fileValidator, UserRepository users,
                          PlatformTransactionManager txManager) {
    this.repository = repository;
    this.ai = ai;
    this.audit = audit;
    this.rateLimiter = rateLimiter;
    this.invoices = invoices;
    this.fileValidator = fileValidator;
    this.users = users;
    this.tx = new TransactionTemplate(txManager);
  }

  // ----------------------------------------------------------------- ingest
  public DocumentDto addText(String title, String filename, String text, AuthPrincipal me) {
    rateLimiter.check("knowledge-add", me.userId().toString(), 10);
    String cleaned = text == null ? "" : text.strip();
    if (cleaned.length() < 20) throw ApiException.badRequest("too_short", "The document has too little text to be useful");
    List<String> chunks = Chunker.split(cleaned, CHUNK_CHARS, CHUNK_OVERLAP);
    if (chunks.isEmpty()) throw ApiException.badRequest("empty_document", "No text could be found in the document");

    // network calls happen outside any transaction
    List<float[]> vectors = new ArrayList<>();
    String model = "";
    for (int i = 0; i < chunks.size(); i += EMBED_BATCH) {
      List<String> batch = chunks.subList(i, Math.min(i + EMBED_BATCH, chunks.size()));
      EmbedResponse r = ai.embed(batch);
      if (r.vectors() == null || r.vectors().size() != batch.size()) {
        throw new ApiException(HttpStatus.BAD_GATEWAY, "invalid_embedding", "The embedding service returned an unexpected answer");
      }
      r.vectors().forEach(v -> vectors.add(toArray(v)));
      model = r.model();
    }

    UUID id = UUID.randomUUID();
    String safeTitle = title.strip();
    String embeddingModel = model;
    tx.executeWithoutResult(s -> {
      repository.insertDocument(id, me.organizationId(), safeTitle, filename, cleaned.length(), chunks.size(), embeddingModel, me.userId());
      repository.insertChunks(id, me.organizationId(), chunks, vectors);
      audit.log(me.organizationId(), me.userId(), "KNOWLEDGE_DOCUMENT_ADDED", "KNOWLEDGE", id,
          Map.of("title", safeTitle, "chunks", chunks.size(), "chars", cleaned.length()));
    });
    return toDto(repository.find(id, me.organizationId()).orElseThrow(), Map.of(me.userId(), nameOf(me)));
  }

  public DocumentDto addFile(MultipartFile file, String title, AuthPrincipal me) {
    if (file == null || file.isEmpty()) throw ApiException.badRequest("empty_file", "The uploaded file is empty");
    byte[] data;
    try {
      data = file.getBytes();
    } catch (IOException e) {
      throw ApiException.badRequest("unreadable_upload", "The upload could not be read");
    }
    String name = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
    String text;
    if (data.length >= 5 && new String(data, 0, 5, StandardCharsets.ISO_8859_1).equals("%PDF-")) {
      var validated = fileValidator.validate(data, name); // size cap, active-content scan, name sanitising
      text = ai.ocr(validated.data(), validated.safeName()).text();
      name = validated.safeName();
    } else {
      if (data.length > MAX_TEXT_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "file_too_large", "Text files may be at most 2 MB");
      text = decodeText(data);
    }
    String effectiveTitle = title == null || title.isBlank() ? stripExtension(name) : title;
    return addText(effectiveTitle.length() > 300 ? effectiveTitle.substring(0, 300) : effectiveTitle, name, text, me);
  }

  // ------------------------------------------------------------------- read
  public List<DocumentDto> list(AuthPrincipal me) {
    List<DocumentRow> rows = repository.list(me.organizationId());
    Map<UUID, String> names = users.findAllById(rows.stream().map(DocumentRow::createdBy).filter(Objects::nonNull).distinct().toList()).stream()
        .collect(Collectors.toMap(UserEntity::getId, UserEntity::getFullName, (a, b) -> a));
    return rows.stream().map(r -> toDto(r, names)).toList();
  }

  public void delete(UUID id, AuthPrincipal me) {
    if (me.role() != Role.ADMIN) throw ApiException.forbidden("Only admins can remove knowledge documents");
    DocumentRow row = repository.find(id, me.organizationId()).orElseThrow(() -> ApiException.notFound("Document"));
    tx.executeWithoutResult(s -> {
      repository.delete(id, me.organizationId());
      audit.log(me.organizationId(), me.userId(), "KNOWLEDGE_DOCUMENT_DELETED", "KNOWLEDGE", id, Map.of("title", row.title()));
    });
  }

  // -------------------------------------------------------------------- ask
  /** Free question against the organisation's rules (reviewers). */
  public AnswerDto ask(String question, AuthPrincipal me) {
    return answer(question, "No specific invoice is being discussed.", question, me, null);
  }

  /** "Why was this invoice flagged?": the question is grounded in the invoice's own validation findings. */
  public AnswerDto explainInvoice(UUID invoiceId, String question, AuthPrincipal me) {
    InvoiceDetail inv = invoices.get(invoiceId, me); // tenant + ownership check happens here
    String q = question == null || question.isBlank() ? DEFAULT_QUESTION : question.strip();
    StringBuilder facts = new StringBuilder();
    facts.append("Invoice from ").append(orDash(inv.vendorName())).append(", number ").append(orDash(inv.invoiceNumber()))
        .append(", date ").append(inv.invoiceDate() == null ? "-" : inv.invoiceDate())
        .append(", total ").append(inv.total() == null ? "-" : inv.total().toPlainString()).append(' ').append(orDash(inv.currency()))
        .append(". Status: ").append(inv.status()).append(".\n");
    StringBuilder retrieval = new StringBuilder(q).append('\n').append(orDash(inv.vendorName())).append('\n');
    if (inv.issues().isEmpty()) {
      facts.append("No validation issues were recorded.\n");
    } else {
      facts.append("Validation findings:\n");
      inv.issues().forEach(i -> {
        facts.append("- [").append(i.severity()).append("] ").append(i.code()).append(": ").append(i.message()).append('\n');
        retrieval.append(i.code().replace('_', ' ').toLowerCase()).append(". ").append(i.message()).append('\n');
      });
    }
    if (inv.modelReviewReasons() != null && !inv.modelReviewReasons().isEmpty()) {
      facts.append("The extraction model asked for review because: ").append(String.join("; ", inv.modelReviewReasons())).append('\n');
    }
    if (inv.injectionSuspected()) facts.append("The document contains text that looks like instructions to an AI.\n");
    return answer(q, facts.toString(), retrieval.toString(), me, invoiceId);
  }

  private AnswerDto answer(String question, String facts, String retrievalText, AuthPrincipal me, UUID invoiceId) {
    rateLimiter.check("knowledge-ask", me.userId().toString(), 20);
    EmbedResponse embedded = ai.embed(List.of(retrievalText.length() > 6000 ? retrievalText.substring(0, 6000) : retrievalText));
    List<Hit> hits = embedded.vectors() == null || embedded.vectors().isEmpty()
        ? List.of() : repository.search(me.organizationId(), toArray(embedded.vectors().get(0)), TOP_K);

    List<Passage> passages = new ArrayList<>();
    for (int i = 0; i < hits.size(); i++) passages.add(new Passage(i + 1, hits.get(i).title(), hits.get(i).content()));
    ExplainResponse r = ai.explain(new ExplainRequest(question, facts, passages));

    List<Source> sources = new ArrayList<>();
    for (int i = 0; i < hits.size(); i++) {
      Hit h = hits.get(i);
      String excerpt = h.content().length() > 280 ? h.content().substring(0, 277) + "..." : h.content();
      sources.add(new Source(i + 1, h.documentId(), h.title(), excerpt, Math.round(h.score() * 1000) / 1000.0));
    }
    audit.log(me.organizationId(), me.userId(), "KNOWLEDGE_QUERY", invoiceId == null ? "KNOWLEDGE" : AuditService.INVOICE, invoiceId,
        Map.of("passages", hits.size(), "cited", r.citations() == null ? 0 : r.citations().size(),
            "tokens", r.usage() == null ? 0 : r.usage().inputTokens() + r.usage().outputTokens()));
    return new AnswerDto(r.answer(), r.insufficientContext(), r.injectionSuspected(),
        r.citations() == null ? List.of() : r.citations(), sources, r.usage() == null ? null : r.usage().model());
  }

  // ---------------------------------------------------------------- helpers
  private DocumentDto toDto(DocumentRow r, Map<UUID, String> names) {
    return new DocumentDto(r.id(), r.title(), r.sourceFilename(), r.charCount(), r.chunkCount(),
        r.createdBy() == null ? "Deleted user" : names.getOrDefault(r.createdBy(), "Unknown"), r.createdAt());
  }

  private String nameOf(AuthPrincipal me) {
    return users.findById(me.userId()).map(UserEntity::getFullName).orElse(me.email());
  }

  private static float[] toArray(List<Float> v) {
    float[] a = new float[v.size()];
    for (int i = 0; i < a.length; i++) a[i] = v.get(i);
    return a;
  }

  /** Strict UTF-8 without control characters: binary files are rejected instead of embedded as garbage. */
  static String decodeText(byte[] data) {
    try {
      String s = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
      if (s.chars().anyMatch(c -> c < 0x20 && c != '\n' && c != '\r' && c != '\t')) throw new CharacterCodingException();
      return s;
    } catch (CharacterCodingException e) {
      throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_type", "Upload a PDF or a UTF-8 text/markdown file");
    }
  }

  private static String stripExtension(String name) {
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(0, dot) : name;
  }

  private static String orDash(String s) { return s == null || s.isBlank() ? "-" : s; }
}
