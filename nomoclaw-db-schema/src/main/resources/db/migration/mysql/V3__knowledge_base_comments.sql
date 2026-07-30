ALTER TABLE knowledge_base COMMENT = '知识库定义表';
ALTER TABLE knowledge_base
    MODIFY knowledge_base_uid VARCHAR(64) NOT NULL COMMENT '知识库业务ID',
    MODIFY name VARCHAR(255) NOT NULL COMMENT '知识库名称',
    MODIFY description TEXT NULL COMMENT '知识库描述',
    MODIFY status VARCHAR(32) NOT NULL COMMENT '状态：ACTIVE/DISABLED/DELETING/ERROR',
    MODIFY embedding_provider_id VARCHAR(64) NOT NULL COMMENT 'Embedding 服务商ID',
    MODIFY embedding_model_id VARCHAR(128) NOT NULL COMMENT 'Embedding 模型ID',
    MODIFY embedding_dimension INT NOT NULL COMMENT '向量维度',
    MODIFY chunk_size_tokens INT NOT NULL COMMENT '默认分块Token数',
    MODIFY chunk_overlap_tokens INT NOT NULL COMMENT '分块重叠Token数',
    MODIFY retrieval_top_k INT NOT NULL COMMENT '默认召回数量',
    MODIFY similarity_threshold DOUBLE NOT NULL COMMENT '相似度阈值',
    MODIFY document_count INT NOT NULL DEFAULT 0 COMMENT '就绪文档数量',
    MODIFY chunk_count BIGINT NOT NULL DEFAULT 0 COMMENT '就绪分块数量',
    MODIFY created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    MODIFY updated_time DATETIME(3) NOT NULL COMMENT '更新时间';

ALTER TABLE knowledge_document COMMENT = '知识库文档表';
ALTER TABLE knowledge_document
    MODIFY document_uid VARCHAR(64) NOT NULL COMMENT '文档业务ID',
    MODIFY knowledge_base_uid VARCHAR(64) NOT NULL COMMENT '所属知识库ID',
    MODIFY display_name VARCHAR(255) NOT NULL COMMENT '展示名称',
    MODIFY source_type VARCHAR(32) NOT NULL COMMENT '来源类型，首版为UPLOAD',
    MODIFY original_file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    MODIFY content_type VARCHAR(128) NOT NULL COMMENT '文件MIME类型',
    MODIFY file_path VARCHAR(1024) NOT NULL COMMENT '本地文件路径',
    MODIFY size_bytes BIGINT NOT NULL COMMENT '文件字节数',
    MODIFY checksum_sha256 VARCHAR(64) NOT NULL COMMENT '文件SHA-256摘要',
    MODIFY current_version_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '当前可检索版本ID',
    MODIFY status VARCHAR(32) NOT NULL COMMENT '状态：UPLOADED/PROCESSING/READY/FAILED/DELETING',
    MODIFY failure_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '失败错误码',
    MODIFY failure_message TEXT NULL COMMENT '失败原因',
    MODIFY page_count INT NOT NULL DEFAULT 0 COMMENT '解析页数',
    MODIFY chunk_count INT NOT NULL DEFAULT 0 COMMENT '分块数量',
    MODIFY created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    MODIFY updated_time DATETIME(3) NOT NULL COMMENT '更新时间';

ALTER TABLE knowledge_document_version COMMENT = '知识库文档版本表';
ALTER TABLE knowledge_document_version
    MODIFY document_version_uid VARCHAR(64) NOT NULL COMMENT '文档版本业务ID',
    MODIFY document_uid VARCHAR(64) NOT NULL COMMENT '文档业务ID',
    MODIFY version_no INT NOT NULL COMMENT '版本号',
    MODIFY checksum_sha256 VARCHAR(64) NOT NULL COMMENT '文件SHA-256摘要',
    MODIFY parser_version VARCHAR(32) NOT NULL COMMENT '解析器版本',
    MODIFY chunker_version VARCHAR(32) NOT NULL COMMENT '分块器版本',
    MODIFY embedding_model_fingerprint VARCHAR(255) NOT NULL COMMENT 'Embedding模型指纹',
    MODIFY status VARCHAR(32) NOT NULL COMMENT '版本状态',
    MODIFY created_time DATETIME(3) NOT NULL COMMENT '创建时间';

ALTER TABLE knowledge_chunk COMMENT = '知识库文档分块表';
ALTER TABLE knowledge_chunk
    MODIFY chunk_uid VARCHAR(64) NOT NULL COMMENT '分块业务ID',
    MODIFY knowledge_base_uid VARCHAR(64) NOT NULL COMMENT '所属知识库ID',
    MODIFY document_uid VARCHAR(64) NOT NULL COMMENT '所属文档ID',
    MODIFY document_version_uid VARCHAR(64) NOT NULL COMMENT '所属文档版本ID',
    MODIFY chunk_index INT NOT NULL COMMENT '文档内分块序号',
    MODIFY content LONGTEXT NOT NULL COMMENT '分块正文',
    MODIFY token_count INT NOT NULL COMMENT '估算Token数',
    MODIFY content_hash VARCHAR(64) NOT NULL COMMENT '正文SHA-256摘要',
    MODIFY page_from INT NULL COMMENT '起始页码',
    MODIFY page_to INT NULL COMMENT '结束页码',
    MODIFY section_path VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '章节路径',
    MODIFY char_start INT NOT NULL DEFAULT 0 COMMENT '原文起始字符偏移',
    MODIFY char_end INT NOT NULL DEFAULT 0 COMMENT '原文结束字符偏移',
    MODIFY vector_point_id VARCHAR(64) NOT NULL COMMENT 'Qdrant向量点ID',
    MODIFY status VARCHAR(32) NOT NULL COMMENT '分块索引状态',
    MODIFY created_time DATETIME(3) NOT NULL COMMENT '创建时间';

ALTER TABLE knowledge_ingestion_job COMMENT = '知识库导入任务表';
ALTER TABLE knowledge_ingestion_job
    MODIFY job_uid VARCHAR(64) NOT NULL COMMENT '任务业务ID',
    MODIFY knowledge_base_uid VARCHAR(64) NOT NULL COMMENT '知识库ID',
    MODIFY document_uid VARCHAR(64) NOT NULL COMMENT '文档ID',
    MODIFY document_version_uid VARCHAR(64) NOT NULL COMMENT '文档版本ID',
    MODIFY status VARCHAR(32) NOT NULL COMMENT '任务状态',
    MODIFY progress_percent INT NOT NULL DEFAULT 0 COMMENT '处理进度百分比',
    MODIFY total_chunks INT NOT NULL DEFAULT 0 COMMENT '总分块数',
    MODIFY processed_chunks INT NOT NULL DEFAULT 0 COMMENT '已处理分块数',
    MODIFY attempt_count INT NOT NULL DEFAULT 0 COMMENT '执行尝试次数',
    MODIFY failure_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '失败错误码',
    MODIFY failure_message TEXT NULL COMMENT '失败原因',
    MODIFY lease_until DATETIME(3) NULL COMMENT '任务租约到期时间',
    MODIFY started_time DATETIME(3) NULL COMMENT '开始时间',
    MODIFY finished_time DATETIME(3) NULL COMMENT '完成时间',
    MODIFY created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    MODIFY updated_time DATETIME(3) NOT NULL COMMENT '更新时间';

ALTER TABLE agent_knowledge_base_relation COMMENT = 'Agent与知识库绑定表';
ALTER TABLE agent_knowledge_base_relation
    MODIFY agent_uid VARCHAR(64) NOT NULL COMMENT 'Agent业务ID',
    MODIFY knowledge_base_uid VARCHAR(64) NOT NULL COMMENT '知识库业务ID',
    MODIFY enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    MODIFY created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    MODIFY updated_time DATETIME(3) NOT NULL COMMENT '更新时间';

ALTER TABLE conversation_knowledge_base_relation COMMENT = '会话与知识库覆盖绑定表';
ALTER TABLE conversation_knowledge_base_relation
    MODIFY conversation_uid VARCHAR(64) NOT NULL COMMENT '会话业务ID',
    MODIFY knowledge_base_uid VARCHAR(64) NOT NULL COMMENT '知识库业务ID',
    MODIFY mode VARCHAR(16) NOT NULL COMMENT '覆盖模式：INCLUDE/EXCLUDE',
    MODIFY created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    MODIFY updated_time DATETIME(3) NOT NULL COMMENT '更新时间';

ALTER TABLE knowledge_retrieval_log COMMENT = '知识库检索审计日志表';
ALTER TABLE knowledge_retrieval_log
    MODIFY retrieval_uid VARCHAR(64) NOT NULL COMMENT '检索业务ID',
    MODIFY conversation_uid VARCHAR(64) NOT NULL COMMENT '会话业务ID',
    MODIFY message_uid VARCHAR(64) NOT NULL COMMENT '用户消息业务ID',
    MODIFY query_text TEXT NOT NULL COMMENT '检索查询文本',
    MODIFY knowledge_base_uids TEXT NOT NULL COMMENT '参与检索的知识库ID列表JSON',
    MODIFY embedding_model_fingerprint VARCHAR(255) NOT NULL COMMENT 'Embedding模型指纹',
    MODIFY candidate_count INT NOT NULL COMMENT '候选命中数',
    MODIFY selected_count INT NOT NULL COMMENT '最终选中数',
    MODIFY latency_ms BIGINT NOT NULL COMMENT '检索耗时毫秒',
    MODIFY status VARCHAR(32) NOT NULL COMMENT '检索状态',
    MODIFY error_message TEXT NULL COMMENT '错误信息',
    MODIFY created_time DATETIME(3) NOT NULL COMMENT '创建时间';

ALTER TABLE message_knowledge_citation COMMENT = '消息知识库引用表';
ALTER TABLE message_knowledge_citation
    MODIFY message_uid VARCHAR(64) NOT NULL COMMENT '用户消息业务ID',
    MODIFY assistant_message_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Assistant消息业务ID',
    MODIFY retrieval_uid VARCHAR(64) NOT NULL COMMENT '检索业务ID',
    MODIFY chunk_uid VARCHAR(64) NOT NULL COMMENT '引用分块业务ID',
    MODIFY rank_index INT NOT NULL COMMENT '引用排序号',
    MODIFY score DOUBLE NOT NULL COMMENT '向量相似度得分',
    MODIFY created_time DATETIME(3) NOT NULL COMMENT '创建时间';
