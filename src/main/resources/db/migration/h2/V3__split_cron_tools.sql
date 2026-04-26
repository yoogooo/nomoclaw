UPDATE tool_definition
SET status = 'DISABLED', updated_time = CURRENT_TIMESTAMP
WHERE tool_key = 'cron_tool';

UPDATE agent_tool_relation
SET status = 'DISABLED', updated_time = CURRENT_TIMESTAMP
WHERE tool_key = 'cron_tool';

MERGE INTO tool_definition (
    tool_key, display_name, description, risk_level, status, sort_index, config_json, created_time, updated_time
) KEY (tool_key) VALUES
    ('CronCreateTool', '创建定时任务', 'Create a scheduled cron-like automation task.', 'HIGH', 'ACTIVE', 60, '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO tool_definition (
    tool_key, display_name, description, risk_level, status, sort_index, config_json, created_time, updated_time
) KEY (tool_key) VALUES
    ('CronDeleteTool', '删除定时任务', 'Delete a scheduled cron-like automation task.', 'HIGH', 'ACTIVE', 61, '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO tool_definition (
    tool_key, display_name, description, risk_level, status, sort_index, config_json, created_time, updated_time
) KEY (tool_key) VALUES
    ('CronListTool', '查询定时任务', 'List or query scheduled cron-like automation tasks.', 'HIGH', 'ACTIVE', 62, '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO agent_tool_relation (
    relation_uid, agent_uid, tool_key, status, sort_index, config_json, created_time, updated_time
) KEY (relation_uid) VALUES
    ('rel_general_cron_create', 'agent_general_assistant', 'CronCreateTool', 'ACTIVE', 60, '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO agent_tool_relation (
    relation_uid, agent_uid, tool_key, status, sort_index, config_json, created_time, updated_time
) KEY (relation_uid) VALUES
    ('rel_general_cron_delete', 'agent_general_assistant', 'CronDeleteTool', 'ACTIVE', 61, '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

MERGE INTO agent_tool_relation (
    relation_uid, agent_uid, tool_key, status, sort_index, config_json, created_time, updated_time
) KEY (relation_uid) VALUES
    ('rel_general_cron_list', 'agent_general_assistant', 'CronListTool', 'ACTIVE', 62, '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
