DELETE FROM agent_tool_relation
WHERE tool_key = 'cron_create_tool'
  AND EXISTS (SELECT 1 FROM (SELECT 1 FROM agent_tool_relation WHERE tool_key = 'CronCreateTool' LIMIT 1) existing_pascal);

DELETE FROM agent_tool_relation
WHERE tool_key = 'cron_delete_tool'
  AND EXISTS (SELECT 1 FROM (SELECT 1 FROM agent_tool_relation WHERE tool_key = 'CronDeleteTool' LIMIT 1) existing_pascal);

DELETE FROM agent_tool_relation
WHERE tool_key = 'cron_list_tool'
  AND EXISTS (SELECT 1 FROM (SELECT 1 FROM agent_tool_relation WHERE tool_key = 'CronListTool' LIMIT 1) existing_pascal);

DELETE FROM tool_definition
WHERE tool_key = 'cron_create_tool'
  AND EXISTS (SELECT 1 FROM (SELECT 1 FROM tool_definition WHERE tool_key = 'CronCreateTool' LIMIT 1) existing_pascal);

DELETE FROM tool_definition
WHERE tool_key = 'cron_delete_tool'
  AND EXISTS (SELECT 1 FROM (SELECT 1 FROM tool_definition WHERE tool_key = 'CronDeleteTool' LIMIT 1) existing_pascal);

DELETE FROM tool_definition
WHERE tool_key = 'cron_list_tool'
  AND EXISTS (SELECT 1 FROM (SELECT 1 FROM tool_definition WHERE tool_key = 'CronListTool' LIMIT 1) existing_pascal);

UPDATE agent_tool_relation
SET tool_key = 'CronCreateTool', updated_time = NOW(3)
WHERE tool_key = 'cron_create_tool';

UPDATE agent_tool_relation
SET tool_key = 'CronDeleteTool', updated_time = NOW(3)
WHERE tool_key = 'cron_delete_tool';

UPDATE agent_tool_relation
SET tool_key = 'CronListTool', updated_time = NOW(3)
WHERE tool_key = 'cron_list_tool';

UPDATE tool_definition
SET tool_key = 'CronCreateTool',
    display_name = '创建定时任务',
    description = 'Create a scheduled cron-like automation task.',
    risk_level = 'HIGH',
    status = 'ACTIVE',
    sort_index = 60,
    updated_time = NOW(3)
WHERE tool_key = 'cron_create_tool';

UPDATE tool_definition
SET tool_key = 'CronDeleteTool',
    display_name = '删除定时任务',
    description = 'Delete a scheduled cron-like automation task.',
    risk_level = 'HIGH',
    status = 'ACTIVE',
    sort_index = 61,
    updated_time = NOW(3)
WHERE tool_key = 'cron_delete_tool';

UPDATE tool_definition
SET tool_key = 'CronListTool',
    display_name = '查询定时任务',
    description = 'List or query scheduled cron-like automation tasks.',
    risk_level = 'HIGH',
    status = 'ACTIVE',
    sort_index = 62,
    updated_time = NOW(3)
WHERE tool_key = 'cron_list_tool';

INSERT INTO tool_definition (
    tool_key, display_name, description, risk_level, status, sort_index, config_json, created_time, updated_time
) VALUES
    ('CronCreateTool', '创建定时任务', 'Create a scheduled cron-like automation task.', 'HIGH', 'ACTIVE', 60, JSON_OBJECT(), NOW(3), NOW(3)),
    ('CronDeleteTool', '删除定时任务', 'Delete a scheduled cron-like automation task.', 'HIGH', 'ACTIVE', 61, JSON_OBJECT(), NOW(3), NOW(3)),
    ('CronListTool', '查询定时任务', 'List or query scheduled cron-like automation tasks.', 'HIGH', 'ACTIVE', 62, JSON_OBJECT(), NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    description = VALUES(description),
    risk_level = VALUES(risk_level),
    status = VALUES(status),
    sort_index = VALUES(sort_index),
    updated_time = NOW(3);

INSERT INTO agent_tool_relation (
    relation_uid, agent_uid, tool_key, status, sort_index, config_json, created_time, updated_time
) VALUES
    ('rel_general_cron_create', 'agent_general_assistant', 'CronCreateTool', 'ACTIVE', 60, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_general_cron_delete', 'agent_general_assistant', 'CronDeleteTool', 'ACTIVE', 61, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_general_cron_list', 'agent_general_assistant', 'CronListTool', 'ACTIVE', 62, JSON_OBJECT(), NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE
    tool_key = VALUES(tool_key),
    status = VALUES(status),
    sort_index = VALUES(sort_index),
    updated_time = NOW(3);
