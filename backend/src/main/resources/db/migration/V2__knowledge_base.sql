-- Knowledge base for retrieval-augmented explanations ("why was this invoice flagged?").
-- Vectors live in the existing PostgreSQL through the pgvector extension, so no extra service is needed.
-- 1536 dimensions = text-embedding-3-small (ai-service EMBEDDING_DIMENSIONS must match).

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_documents (
  id               UUID PRIMARY KEY,
  organization_id  UUID         NOT NULL REFERENCES organizations(id),
  title            VARCHAR(300) NOT NULL,
  source_filename  VARCHAR(255),
  char_count       INT          NOT NULL,
  chunk_count      INT          NOT NULL,
  embedding_model  VARCHAR(100) NOT NULL,
  created_by       UUID         REFERENCES users(id) ON DELETE SET NULL,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_knowledge_docs_org ON knowledge_documents (organization_id, created_at DESC);

CREATE TABLE knowledge_chunks (
  id               BIGSERIAL PRIMARY KEY,
  document_id      UUID         NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
  organization_id  UUID         NOT NULL,
  position         INT          NOT NULL,
  content          TEXT         NOT NULL,
  embedding        vector(1536) NOT NULL
);
CREATE INDEX ix_knowledge_chunks_org ON knowledge_chunks (organization_id);
CREATE INDEX ix_knowledge_chunks_embedding ON knowledge_chunks USING hnsw (embedding vector_cosine_ops);
