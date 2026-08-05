CREATE TABLE IF NOT EXISTS token_usage_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_uid VARCHAR(64) NOT NULL DEFAULT '',
    scene VARCHAR(32) NOT NULL DEFAULT '',
    provider VARCHAR(64) NOT NULL DEFAULT '',
    model_name VARCHAR(128) NOT NULL DEFAULT '',
    conversation_uid VARCHAR(64) NOT NULL DEFAULT '',
    message_uid VARCHAR(64) NOT NULL DEFAULT '',
    input_tokens INT NOT NULL DEFAULT 0,
    cached_input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    usage_available BOOLEAN NOT NULL DEFAULT FALSE,
    occurred_time TIMESTAMP NOT NULL,
    CONSTRAINT uk_token_usage_record_uid UNIQUE (record_uid)
);
CREATE INDEX idx_token_usage_record_time ON token_usage_record(occurred_time);
CREATE INDEX idx_token_usage_record_scene_time ON token_usage_record(scene, occurred_time);
CREATE INDEX idx_token_usage_record_model_time ON token_usage_record(provider, model_name, occurred_time);
CREATE INDEX idx_token_usage_record_conversation ON token_usage_record(conversation_uid, occurred_time);
