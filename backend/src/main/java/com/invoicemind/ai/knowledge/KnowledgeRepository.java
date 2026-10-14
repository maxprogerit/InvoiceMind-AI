package com.invoicemind.ai.knowledge;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** pgvector access through plain SQL (the vector type needs no extra ORM dependency). Every query is organisation-scoped. */
@Repository
@RequiredArgsConstructor
public class KnowledgeRepository {
  public record DocumentRow(UUID id, String title, String sourceFilename, int charCount, int chunkCount,
                            String embeddingModel, UUID createdBy, Instant createdAt) {}

  public record Hit(long chunkId, UUID documentId, String title, int position, String content, double score) {}

  private final JdbcTemplate jdbc;

  public void insertDocument(UUID id, UUID org, String title, String filename, int chars, int chunks, String model, UUID by) {
    jdbc.update("""
        insert into knowledge_documents (id, organization_id, title, source_filename, char_count, chunk_count, embedding_model, created_by)
        values (?, ?, ?, ?, ?, ?, ?, ?)""", id, org, title, filename, chars, chunks, model, by);
  }

  public void insertChunks(UUID documentId, UUID org, List<String> contents, List<float[]> vectors) {
    jdbc.batchUpdate("insert into knowledge_chunks (document_id, organization_id, position, content, embedding) values (?, ?, ?, ?, cast(? as vector))",
        contents.size() == 0 ? List.of() : java.util.stream.IntStream.range(0, contents.size())
            .mapToObj(i -> new Object[]{documentId, org, i, contents.get(i), literal(vectors.get(i))}).toList());
  }

  public List<DocumentRow> list(UUID org) {
    return jdbc.query("select * from knowledge_documents where organization_id = ? order by created_at desc", (rs, i) -> toDoc(rs), org);
  }

  public Optional<DocumentRow> find(UUID id, UUID org) {
    return jdbc.query("select * from knowledge_documents where id = ? and organization_id = ?", (rs, i) -> toDoc(rs), id, org).stream().findFirst();
  }

  public boolean delete(UUID id, UUID org) {
    return jdbc.update("delete from knowledge_documents where id = ? and organization_id = ?", id, org) > 0;
  }

  /** Nearest chunks by cosine distance; score = cosine similarity (1 = identical direction). */
  public List<Hit> search(UUID org, float[] query, int limit) {
    String q = literal(query);
    return jdbc.query("""
        select c.id, c.document_id, d.title, c.position, c.content, 1 - (c.embedding <=> cast(? as vector)) as score
        from knowledge_chunks c join knowledge_documents d on d.id = c.document_id
        where c.organization_id = ?
        order by c.embedding <=> cast(? as vector)
        limit ?""",
        (rs, i) -> new Hit(rs.getLong("id"), rs.getObject("document_id", UUID.class), rs.getString("title"), rs.getInt("position"),
            rs.getString("content"), rs.getDouble("score")), q, org, q, limit);
  }

  private static DocumentRow toDoc(java.sql.ResultSet rs) throws java.sql.SQLException {
    Timestamp created = rs.getTimestamp("created_at");
    return new DocumentRow(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("source_filename"),
        rs.getInt("char_count"), rs.getInt("chunk_count"), rs.getString("embedding_model"),
        rs.getObject("created_by", UUID.class), created.toInstant());
  }

  static String literal(float[] v) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < v.length; i++) {
      if (i > 0) sb.append(',');
      sb.append(v[i]);
    }
    return sb.append(']').toString();
  }
}
