-- LLM traces are provider-neutral. Remove legacy Codex-specific protocol columns.
SELECT GROUP_CONCAT(CONCAT('DROP COLUMN `', column_name, '`') SEPARATOR ', ')
INTO @llm_trace_drop_columns
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'llm_trace'
  AND column_name IN (
      'codex_request_body_raw',
      'codex_request_body_safe',
      'codex_request_headers_raw',
      'codex_request_body_sha256',
      'codex_request_body_bytes',
      'codex_response_headers',
      'codex_response_headers_raw',
      'codex_response_body_raw',
      'codex_response_body_safe',
      'codex_response_body_sha256',
      'codex_response_body_bytes',
      'codex_response_body_complete',
      'codex_parsed_stream_events'
  );

SET @llm_trace_drop_sql = IF(
    @llm_trace_drop_columns IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE llm_trace ', @llm_trace_drop_columns)
);

PREPARE llm_trace_drop_statement FROM @llm_trace_drop_sql;
EXECUTE llm_trace_drop_statement;
DEALLOCATE PREPARE llm_trace_drop_statement;
