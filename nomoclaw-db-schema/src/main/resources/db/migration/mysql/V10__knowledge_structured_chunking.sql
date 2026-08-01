ALTER TABLE knowledge_import_batch
    ADD COLUMN chunk_strategy VARCHAR(32) NOT NULL DEFAULT 'TOKEN' AFTER parser_mode;

ALTER TABLE knowledge_document_version
    ADD COLUMN chunk_strategy VARCHAR(32) NOT NULL DEFAULT 'TOKEN' AFTER parser_mode;

ALTER TABLE knowledge_chunk
    ADD COLUMN document_node_uid VARCHAR(64) NULL AFTER document_version_uid,
    ADD KEY idx_knowledge_chunk_node (document_node_uid);

CREATE TABLE knowledge_document_node (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    node_uid VARCHAR(64) NOT NULL UNIQUE,
    knowledge_base_uid VARCHAR(64) NOT NULL,
    document_uid VARCHAR(64) NOT NULL,
    document_version_uid VARCHAR(64) NOT NULL,
    parent_node_uid VARCHAR(64) NULL,
    node_type VARCHAR(32) NOT NULL,
    level INT NOT NULL DEFAULT 0,
    code VARCHAR(128) NOT NULL DEFAULT '',
    title VARCHAR(1024) NOT NULL DEFAULT '',
    section_path VARCHAR(2048) NOT NULL DEFAULT '',
    page_from INT NULL,
    page_to INT NULL,
    char_start INT NOT NULL DEFAULT 0,
    char_end INT NOT NULL DEFAULT 0,
    detection_source VARCHAR(32) NOT NULL DEFAULT 'FALLBACK',
    confidence DECIMAL(5,4) NOT NULL DEFAULT 0,
    indexable TINYINT(1) NOT NULL DEFAULT 1,
    metadata_json JSON NULL,
    created_time DATETIME(3) NOT NULL,
    KEY idx_knowledge_node_document_version (document_version_uid, id),
    KEY idx_knowledge_node_parent (parent_node_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
