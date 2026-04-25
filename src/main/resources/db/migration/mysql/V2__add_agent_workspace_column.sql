SET @has_workspace := (
    SELECT COUNT(1)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'agent_definition'
      AND column_name = 'workspace'
);

SET @ddl_workspace := IF(
    @has_workspace > 0,
    'SELECT 1',
    'ALTER TABLE agent_definition ADD COLUMN workspace VARCHAR(1024) NOT NULL DEFAULT '''' COMMENT ''Agent 工作区绝对路径'' AFTER status'
);

PREPARE stmt_workspace FROM @ddl_workspace;
EXECUTE stmt_workspace;
DEALLOCATE PREPARE stmt_workspace;

UPDATE agent_definition
SET workspace = ''
WHERE workspace IS NULL;
