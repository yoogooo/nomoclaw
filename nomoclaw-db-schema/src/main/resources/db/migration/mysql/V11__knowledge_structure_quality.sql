ALTER TABLE knowledge_document_node
    ADD COLUMN node_role VARCHAR(32) NOT NULL DEFAULT 'ROOT' AFTER indexable,
    ADD COLUMN source_order INT NOT NULL DEFAULT 0 AFTER node_role,
    ADD COLUMN quality_score DECIMAL(5,4) NOT NULL DEFAULT 0 AFTER source_order,
    ADD COLUMN parent_confidence DECIMAL(5,4) NOT NULL DEFAULT 0 AFTER quality_score,
    ADD COLUMN indexable_reason VARCHAR(64) NOT NULL DEFAULT '' AFTER parent_confidence;
