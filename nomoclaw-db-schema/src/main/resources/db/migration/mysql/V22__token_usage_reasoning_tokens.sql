ALTER TABLE token_usage_record ADD COLUMN reasoning_tokens INT NOT NULL DEFAULT 0 AFTER output_tokens;
