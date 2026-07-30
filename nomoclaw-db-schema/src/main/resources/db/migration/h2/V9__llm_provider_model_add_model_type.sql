ALTER TABLE llm_provider_model
    ADD COLUMN model_type VARCHAR(32) NOT NULL DEFAULT 'CHAT' AFTER model_name;
