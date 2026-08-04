ALTER TABLE knowledge_document_node ADD COLUMN IF NOT EXISTS node_role VARCHAR(32) DEFAULT 'ROOT' NOT NULL;
ALTER TABLE knowledge_document_node ADD COLUMN IF NOT EXISTS source_order INT DEFAULT 0 NOT NULL;
ALTER TABLE knowledge_document_node ADD COLUMN IF NOT EXISTS quality_score DECIMAL(5,4) DEFAULT 0 NOT NULL;
ALTER TABLE knowledge_document_node ADD COLUMN IF NOT EXISTS parent_confidence DECIMAL(5,4) DEFAULT 0 NOT NULL;
ALTER TABLE knowledge_document_node ADD COLUMN IF NOT EXISTS indexable_reason VARCHAR(64) DEFAULT '' NOT NULL;
