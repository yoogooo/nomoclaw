SET @has_output_text := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_step'
      AND column_name = 'output_text'
);

SET @ddl_output_text := IF(
    @has_output_text > 0,
    'SELECT 1',
    'ALTER TABLE agent_step ADD COLUMN output_text LONGTEXT NULL COMMENT ''步骤最终输出文本'' AFTER last_error'
);

PREPARE stmt_output_text FROM @ddl_output_text;
EXECUTE stmt_output_text;
DEALLOCATE PREPARE stmt_output_text;
