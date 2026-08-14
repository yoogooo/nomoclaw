ALTER TABLE llm_trace ADD COLUMN reasoning_tokens INT NOT NULL DEFAULT 0 AFTER output_tokens;
