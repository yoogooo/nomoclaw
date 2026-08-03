ALTER TABLE knowledge_document_node
    ADD COLUMN node_role VARCHAR(32) NOT NULL DEFAULT 'ROOT' COMMENT '节点角色：ROOT/FRONT_MATTER/TABLE_OF_CONTENTS/CHAPTER/SECTION/SUBSECTION/APPENDIX' AFTER indexable,
    ADD COLUMN source_order INT NOT NULL DEFAULT 0 COMMENT '节点在解析结果中的原始顺序' AFTER node_role,
    ADD COLUMN quality_score DECIMAL(5,4) NOT NULL DEFAULT 0 COMMENT '节点结构质量评分，范围0到1' AFTER source_order,
    ADD COLUMN parent_confidence DECIMAL(5,4) NOT NULL DEFAULT 0 COMMENT '父节点识别置信度，范围0到1' AFTER quality_score,
    ADD COLUMN indexable_reason VARCHAR(64) NOT NULL DEFAULT '' COMMENT '是否参与索引的原因' AFTER parent_confidence;
