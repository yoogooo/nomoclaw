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
    status = VALUES(status),
    sort_index = VALUES(sort_index),
    updated_time = NOW(3);
