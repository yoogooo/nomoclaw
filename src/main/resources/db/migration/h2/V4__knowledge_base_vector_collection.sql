ALTER TABLE knowledge_base
    ADD COLUMN IF NOT EXISTS vector_collection_name VARCHAR(128) NOT NULL DEFAULT '';

COMMENT ON COLUMN knowledge_base.vector_collection_name IS 'Qdrant Collection 名称';

CREATE INDEX IF NOT EXISTS idx_knowledge_base_vector_collection_name ON knowledge_base(vector_collection_name);
