UPDATE tool_definition
SET status = 'DISABLED', updated_time = NOW(3)
WHERE tool_key = 'cron_tool';

UPDATE agent_tool_relation
SET status = 'DISABLED', updated_time = NOW(3)
WHERE tool_key = 'cron_tool';

INSERT INTO tool_definition (
    tool_key, display_name, description, risk_level, status, sort_index, config_json, created_time, updated_time
) VALUES
    ('cron_create_tool', '创建定时任务', 'Create a scheduled cron-like automation task.', 'HIGH', 'ACTIVE', 60, JSON_OBJECT(), NOW(3), NOW(3)),
    ('cron_delete_tool', '删除定时任务', 'Delete a scheduled cron-like automation task.', 'HIGH', 'ACTIVE', 61, JSON_OBJECT(), NOW(3), NOW(3)),
    ('cron_list_tool', '查询定时任务', 'List or query scheduled cron-like automation tasks.', 'HIGH', 'ACTIVE', 62, JSON_OBJECT(), NOW(3), NOW(3))
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
    ('rel_general_cron_create', 'agent_general_assistant', 'cron_create_tool', 'ACTIVE', 60, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_general_cron_delete', 'agent_general_assistant', 'cron_delete_tool', 'ACTIVE', 61, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_general_cron_list', 'agent_general_assistant', 'cron_list_tool', 'ACTIVE', 62, JSON_OBJECT(), NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    sort_index = VALUES(sort_index),
    updated_time = NOW(3);
