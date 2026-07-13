CREATE TABLE knowledge_base (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, knowledge_base_uid VARCHAR(64) NOT NULL UNIQUE,
 name VARCHAR(255) NOT NULL, description TEXT NULL, status VARCHAR(32) NOT NULL,
 embedding_provider_id VARCHAR(64) NOT NULL, embedding_model_id VARCHAR(128) NOT NULL, embedding_dimension INT NOT NULL,
 chunk_size_tokens INT NOT NULL, chunk_overlap_tokens INT NOT NULL, retrieval_top_k INT NOT NULL,
 similarity_threshold DOUBLE NOT NULL, document_count INT NOT NULL DEFAULT 0, chunk_count BIGINT NOT NULL DEFAULT 0,
 created_time DATETIME(3) NOT NULL, updated_time DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE knowledge_document (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, document_uid VARCHAR(64) NOT NULL UNIQUE, knowledge_base_uid VARCHAR(64) NOT NULL,
 display_name VARCHAR(255) NOT NULL, source_type VARCHAR(32) NOT NULL, original_file_name VARCHAR(255) NOT NULL,
 content_type VARCHAR(128) NOT NULL, file_path VARCHAR(1024) NOT NULL, size_bytes BIGINT NOT NULL, checksum_sha256 VARCHAR(64) NOT NULL,
 current_version_uid VARCHAR(64) NOT NULL DEFAULT '', status VARCHAR(32) NOT NULL, failure_code VARCHAR(64) NOT NULL DEFAULT '',
 failure_message TEXT NULL, page_count INT NOT NULL DEFAULT 0, chunk_count INT NOT NULL DEFAULT 0,
 created_time DATETIME(3) NOT NULL, updated_time DATETIME(3) NOT NULL,
 UNIQUE KEY uk_knowledge_document_checksum (knowledge_base_uid, checksum_sha256), KEY idx_knowledge_document_base (knowledge_base_uid, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE knowledge_document_version (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, document_version_uid VARCHAR(64) NOT NULL UNIQUE, document_uid VARCHAR(64) NOT NULL,
 version_no INT NOT NULL, checksum_sha256 VARCHAR(64) NOT NULL, parser_version VARCHAR(32) NOT NULL, chunker_version VARCHAR(32) NOT NULL,
 embedding_model_fingerprint VARCHAR(255) NOT NULL, status VARCHAR(32) NOT NULL, created_time DATETIME(3) NOT NULL,
 UNIQUE KEY uk_knowledge_document_version (document_uid, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE knowledge_chunk (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, chunk_uid VARCHAR(64) NOT NULL UNIQUE, knowledge_base_uid VARCHAR(64) NOT NULL,
 document_uid VARCHAR(64) NOT NULL, document_version_uid VARCHAR(64) NOT NULL, chunk_index INT NOT NULL, content LONGTEXT NOT NULL,
 token_count INT NOT NULL, content_hash VARCHAR(64) NOT NULL, page_from INT NULL, page_to INT NULL, section_path VARCHAR(1024) NOT NULL DEFAULT '',
 char_start INT NOT NULL DEFAULT 0, char_end INT NOT NULL DEFAULT 0, vector_point_id VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL,
 created_time DATETIME(3) NOT NULL, KEY idx_knowledge_chunk_base_document (knowledge_base_uid, document_uid),
 KEY idx_knowledge_chunk_version (document_version_uid), KEY idx_knowledge_chunk_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE knowledge_ingestion_job (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, job_uid VARCHAR(64) NOT NULL UNIQUE, knowledge_base_uid VARCHAR(64) NOT NULL,
 document_uid VARCHAR(64) NOT NULL, document_version_uid VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL,
 progress_percent INT NOT NULL DEFAULT 0, total_chunks INT NOT NULL DEFAULT 0, processed_chunks INT NOT NULL DEFAULT 0,
 attempt_count INT NOT NULL DEFAULT 0, failure_code VARCHAR(64) NOT NULL DEFAULT '', failure_message TEXT NULL,
 lease_until DATETIME(3) NULL, started_time DATETIME(3) NULL, finished_time DATETIME(3) NULL,
 created_time DATETIME(3) NOT NULL, updated_time DATETIME(3) NOT NULL, KEY idx_knowledge_job_status (status, lease_until, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE agent_knowledge_base_relation (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, agent_uid VARCHAR(64) NOT NULL, knowledge_base_uid VARCHAR(64) NOT NULL,
 enabled TINYINT(1) NOT NULL DEFAULT 1, created_time DATETIME(3) NOT NULL, updated_time DATETIME(3) NOT NULL,
 UNIQUE KEY uk_agent_knowledge_base (agent_uid, knowledge_base_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE conversation_knowledge_base_relation (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, conversation_uid VARCHAR(64) NOT NULL, knowledge_base_uid VARCHAR(64) NOT NULL,
 mode VARCHAR(16) NOT NULL, created_time DATETIME(3) NOT NULL, updated_time DATETIME(3) NOT NULL,
 UNIQUE KEY uk_conversation_knowledge_base (conversation_uid, knowledge_base_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE knowledge_retrieval_log (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, retrieval_uid VARCHAR(64) NOT NULL UNIQUE, conversation_uid VARCHAR(64) NOT NULL,
 message_uid VARCHAR(64) NOT NULL, query_text TEXT NOT NULL, knowledge_base_uids TEXT NOT NULL, embedding_model_fingerprint VARCHAR(255) NOT NULL,
 candidate_count INT NOT NULL, selected_count INT NOT NULL, latency_ms BIGINT NOT NULL, status VARCHAR(32) NOT NULL,
 error_message TEXT NULL, created_time DATETIME(3) NOT NULL, KEY idx_knowledge_retrieval_message (message_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE message_knowledge_citation (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, message_uid VARCHAR(64) NOT NULL, assistant_message_uid VARCHAR(64) NOT NULL DEFAULT '',
 retrieval_uid VARCHAR(64) NOT NULL, chunk_uid VARCHAR(64) NOT NULL, rank_index INT NOT NULL, score DOUBLE NOT NULL,
 created_time DATETIME(3) NOT NULL, UNIQUE KEY uk_message_knowledge_citation (message_uid, retrieval_uid, chunk_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
