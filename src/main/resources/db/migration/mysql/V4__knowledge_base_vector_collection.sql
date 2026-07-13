ALTER TABLE knowledge_base
    ADD COLUMN vector_collection_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Qdrant Collection 名称' AFTER embedding_dimension;

CREATE INDEX idx_knowledge_base_vector_collection_name ON knowledge_base (vector_collection_name);
