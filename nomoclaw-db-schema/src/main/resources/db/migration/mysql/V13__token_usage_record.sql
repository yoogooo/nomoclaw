CREATE TABLE IF NOT EXISTS token_usage_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    record_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '用量记录业务ID',
    scene VARCHAR(32) NOT NULL DEFAULT '' COMMENT '调用场景',
    provider VARCHAR(64) NOT NULL DEFAULT '' COMMENT '模型服务商',
    model_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '模型名称',
    conversation_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '关联会话ID',
    message_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '关联消息ID',
    input_tokens INT NOT NULL DEFAULT 0 COMMENT '输入 token 数',
    cached_input_tokens INT NOT NULL DEFAULT 0 COMMENT '缓存命中输入 token 数',
    output_tokens INT NOT NULL DEFAULT 0 COMMENT '输出 token 数',
    total_tokens INT NOT NULL DEFAULT 0 COMMENT '总 token 数',
    usage_available TINYINT(1) NOT NULL DEFAULT 0 COMMENT '供应商是否返回用量',
    occurred_time DATETIME(3) NOT NULL COMMENT '调用完成时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_token_usage_record_uid (record_uid),
    KEY idx_token_usage_record_time (occurred_time),
    KEY idx_token_usage_record_scene_time (scene, occurred_time),
    KEY idx_token_usage_record_model_time (provider, model_name, occurred_time),
    KEY idx_token_usage_record_conversation (conversation_uid, occurred_time)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='模型 Token 用量流水';
