CREATE TABLE knowledge_import_batch (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    batch_uid VARCHAR(64) NOT NULL UNIQUE,
    knowledge_base_uid VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    parser_mode VARCHAR(32) NOT NULL DEFAULT 'STRUCTURED',
    chunk_size_tokens INT NOT NULL DEFAULT 500,
    chunk_overlap_tokens INT NOT NULL DEFAULT 80,
    embedding_provider_id VARCHAR(64) NOT NULL DEFAULT '',
    embedding_model_id VARCHAR(128) NOT NULL DEFAULT '',
    embedding_dimension INT NOT NULL DEFAULT 0,
    embedding_model_fingerprint VARCHAR(512) NOT NULL DEFAULT '',
    config_hash VARCHAR(64) NOT NULL DEFAULT '',
    created_time DATETIME(3) NOT NULL,
    updated_time DATETIME(3) NOT NULL,
    KEY idx_knowledge_import_batch_base (knowledge_base_uid, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE knowledge_import_item (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    item_uid VARCHAR(64) NOT NULL UNIQUE,
    batch_uid VARCHAR(64) NOT NULL,
    document_uid VARCHAR(64) NULL,
    document_version_uid VARCHAR(64) NULL,
    original_file_name VARCHAR(255) NOT NULL,
    mode VARCHAR(16) NOT NULL DEFAULT 'UPLOAD',
    outcome VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(64) NOT NULL DEFAULT '',
    error_message TEXT NULL,
    created_time DATETIME(3) NOT NULL,
    updated_time DATETIME(3) NOT NULL,
    KEY idx_knowledge_import_item_batch (batch_uid, id),
    KEY idx_knowledge_import_item_version (document_version_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE knowledge_document_version
    ADD COLUMN parser_mode VARCHAR(32) NOT NULL DEFAULT 'STRUCTURED' AFTER chunker_version,
    ADD COLUMN chunk_size_tokens INT NOT NULL DEFAULT 500 AFTER parser_mode,
    ADD COLUMN chunk_overlap_tokens INT NOT NULL DEFAULT 80 AFTER chunk_size_tokens,
    ADD COLUMN build_mode VARCHAR(16) NOT NULL DEFAULT 'INITIAL' AFTER chunk_overlap_tokens,
    ADD COLUMN embedding_provider_id VARCHAR(64) NOT NULL DEFAULT '' AFTER build_mode,
    ADD COLUMN embedding_model_id VARCHAR(128) NOT NULL DEFAULT '' AFTER embedding_provider_id,
    ADD COLUMN embedding_dimension INT NOT NULL DEFAULT 0 AFTER embedding_model_id,
    ADD COLUMN parse_warnings TEXT NULL AFTER embedding_dimension;

ALTER TABLE knowledge_ingestion_job
    ADD COLUMN processed_pages INT NOT NULL DEFAULT 0 AFTER processed_chunks,
    ADD COLUMN total_pages INT NOT NULL DEFAULT 0 AFTER processed_pages,
    ADD COLUMN cache_hit_chunks INT NOT NULL DEFAULT 0 AFTER total_pages,
    ADD COLUMN cache_miss_chunks INT NOT NULL DEFAULT 0 AFTER cache_hit_chunks;

UPDATE knowledge_document_version v
JOIN knowledge_document d ON d.document_uid = v.document_uid
JOIN knowledge_base b ON b.knowledge_base_uid = d.knowledge_base_uid
SET v.parser_mode = 'STRUCTURED',
    v.chunk_size_tokens = b.chunk_size_tokens,
    v.chunk_overlap_tokens = b.chunk_overlap_tokens,
    v.embedding_provider_id = b.embedding_provider_id,
    v.embedding_model_id = b.embedding_model_id,
    v.embedding_dimension = b.embedding_dimension
WHERE v.embedding_provider_id = '' OR v.embedding_dimension = 0;

CREATE TABLE knowledge_embedding_cache (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    cache_key VARCHAR(64) NOT NULL UNIQUE,
    model_fingerprint VARCHAR(512) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    dimension INT NOT NULL,
    vector_blob LONGBLOB NOT NULL,
    vector_bytes INT NOT NULL,
    hit_count BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME(3) NOT NULL,
    last_access_time DATETIME(3) NOT NULL,
    KEY idx_knowledge_embedding_cache_cleanup (last_access_time, id),
    KEY idx_knowledge_embedding_cache_content (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
