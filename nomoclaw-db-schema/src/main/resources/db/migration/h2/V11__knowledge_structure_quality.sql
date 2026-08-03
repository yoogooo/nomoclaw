ALTER TABLE knowledge_document_node ADD COLUMN IF NOT EXISTS node_role VARCHAR(32) DEFAULT 'ROOT' NOT NULL;
ALTER TABLE knowledge_document_node ADD COLUMN IF NOT EXISTS source_order INT DEFAULT 0 NOT NULL;
ALTER TABLE knowledge_document_node ADD COLUMN IF NOT EXISTS quality_score DECIMAL(5,4) DEFAULT 0 NOT NULL;
ALTER TABLE knowledge_document_node ADD COLUMN IF NOT EXISTS parent_confidence DECIMAL(5,4) DEFAULT 0 NOT NULL;
ALTER TABLE knowledge_document_node ADD COLUMN IF NOT EXISTS indexable_reason VARCHAR(64) DEFAULT '' NOT NULL;

COMMENT ON COLUMN knowledge_document_node.node_role IS '节点角色：ROOT/FRONT_MATTER/TABLE_OF_CONTENTS/CHAPTER/SECTION/SUBSECTION/APPENDIX';
COMMENT ON COLUMN knowledge_document_node.source_order IS '节点在解析结果中的原始顺序';
COMMENT ON COLUMN knowledge_document_node.quality_score IS '节点结构质量评分，范围0到1';
COMMENT ON COLUMN knowledge_document_node.parent_confidence IS '父节点识别置信度，范围0到1';
COMMENT ON COLUMN knowledge_document_node.indexable_reason IS '是否参与索引的原因';
