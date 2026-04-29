DROP TABLE IF EXISTS llm_provider_model;
DROP TABLE IF EXISTS llm_provider_config;
DROP TABLE IF EXISTS agent_cron_job;
DROP TABLE IF EXISTS agent_cron_subscription;
DROP TABLE IF EXISTS agent_tip;
DROP TABLE IF EXISTS agent_skill_relation;
DROP TABLE IF EXISTS skill_definition;
DROP TABLE IF EXISTS agent_mcp_tool_relation;
DROP TABLE IF EXISTS agent_tool_relation;
DROP TABLE IF EXISTS tool_definition;
DROP TABLE IF EXISTS mcp_tool_snapshot;
DROP TABLE IF EXISTS mcp_server_definition;
DROP TABLE IF EXISTS agent_group_member;
DROP TABLE IF EXISTS agent_group_definition;
DROP TABLE IF EXISTS agent_definition;
DROP TABLE IF EXISTS system_error_log;
DROP TABLE IF EXISTS agent_event;
DROP TABLE IF EXISTS agent_step;
DROP TABLE IF EXISTS agent_message_attachment;
DROP TABLE IF EXISTS agent_message;
DROP TABLE IF EXISTS agent_conversation;
DROP TABLE IF EXISTS agent_channel_inbound_log;
DROP TABLE IF EXISTS agent_channel_session;

CREATE TABLE IF NOT EXISTS agent_group_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    agent_group_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Agent 群组业务ID',
    group_name VARCHAR(64) NOT NULL DEFAULT '' COMMENT '群组名称标识',
    display_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '群组展示名称',
    avatar VARCHAR(512) NOT NULL DEFAULT '' COMMENT '群组头像，可为本地路径、URL 或图标标识',
    description VARCHAR(512) NOT NULL DEFAULT '' COMMENT '群组描述',
    scene_tags JSON NOT NULL COMMENT '群组场景标签JSON数组，例如 [\"research\",\"delivery\"]',
    collaboration_mode VARCHAR(64) NOT NULL DEFAULT '' COMMENT '群内协作模式，例如 pipeline / parallel / debate',
    min_agent_count INT NOT NULL DEFAULT 2 COMMENT '群组最少 Agent 数，默认至少 2 个',
    max_agent_count INT NOT NULL DEFAULT 0 COMMENT '群组最大 Agent 数，0 表示不限制',
    owner_agent_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '群主或默认主控 Agent UID',
    sort_index INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    ext_config JSON NULL COMMENT '扩展配置JSON',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_group_definition_uid (agent_group_uid),
    UNIQUE KEY uk_agent_group_definition_name (group_name),
    KEY idx_agent_group_definition_status (status) COMMENT '按状态查询群组',
    KEY idx_agent_group_definition_sort (sort_index) COMMENT '按排序查询群组'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent 群组定义表';

CREATE TABLE IF NOT EXISTS agent_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    agent_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Agent 业务ID',
    agent_name VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Agent 名称标识',
    display_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Agent 展示名称',
    avatar VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'Agent 头像，可为本地路径、URL 或图标标识',
    description VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'Agent 描述',
    capability_tags JSON NOT NULL COMMENT '能力标签JSON数组，例如 [\"browser\",\"file\",\"command\"]',
    prompt_profile VARCHAR(128) NOT NULL DEFAULT '' COMMENT '提示词画像或角色标识',
    model_provider_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '默认模型提供方ID',
    model_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '默认模型ID',
    sort_index INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    is_group_entry TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为群组入口节点：0否 1是',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    workspace VARCHAR(1024) NOT NULL DEFAULT '' COMMENT 'Agent 工作区绝对路径',
    ext_config JSON NULL COMMENT '扩展配置JSON',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_definition_uid (agent_uid),
    UNIQUE KEY uk_agent_definition_name (agent_name),
    KEY idx_agent_definition_status (status) COMMENT '按状态查询 Agent'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent 基础定义表';

CREATE TABLE IF NOT EXISTS tool_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    tool_key VARCHAR(64) NOT NULL DEFAULT '' COMMENT '工具唯一标识',
    display_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '工具展示名称',
    description VARCHAR(512) NOT NULL DEFAULT '' COMMENT '工具描述',
    risk_level VARCHAR(16) NOT NULL DEFAULT '' COMMENT '风险级别标记',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    sort_index INT NOT NULL DEFAULT 0 COMMENT '排序值',
    config_json JSON NULL COMMENT '扩展配置',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_definition_key (tool_key),
    KEY idx_tool_definition_status (status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='工具定义表';

CREATE TABLE IF NOT EXISTS agent_tool_relation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    relation_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '关联业务ID',
    agent_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Agent 业务ID',
    tool_key VARCHAR(64) NOT NULL DEFAULT '' COMMENT '工具唯一标识',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    sort_index INT NOT NULL DEFAULT 0 COMMENT '排序值',
    config_json JSON NULL COMMENT '扩展配置',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_tool_relation_uid (relation_uid),
    UNIQUE KEY uk_agent_tool_relation_pair (agent_uid, tool_key),
    KEY idx_agent_tool_relation_agent (agent_uid, status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent 与工具关联表';

CREATE TABLE IF NOT EXISTS agent_mcp_tool_relation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    relation_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '关联业务ID',
    agent_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Agent 业务ID',
    tool_key VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'MCP 工具唯一标识',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    sort_index INT NOT NULL DEFAULT 0 COMMENT '排序值',
    config_json JSON NULL COMMENT '扩展配置',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_mcp_tool_relation_uid (relation_uid),
    UNIQUE KEY uk_agent_mcp_tool_relation_pair (agent_uid, tool_key),
    KEY idx_agent_mcp_tool_relation_agent (agent_uid, status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent 与 MCP 工具关联表';

CREATE TABLE IF NOT EXISTS mcp_server_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    server_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'MCP Server 业务ID',
    server_name VARCHAR(100) NOT NULL DEFAULT '' COMMENT 'MCP Server 唯一名称',
    transport VARCHAR(16) NOT NULL DEFAULT 'HTTP' COMMENT '传输方式：HTTP/STDIO',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    timeout_seconds INT NOT NULL DEFAULT 30 COMMENT '连接和调用超时时间',
    auto_start TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否自动启动或连接',
    config_json JSON NULL COMMENT 'transport 配置',
    last_connected_time DATETIME(3) NULL COMMENT '最近成功连接时间',
    last_error TEXT NULL COMMENT '最近错误',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mcp_server_uid (server_uid),
    UNIQUE KEY uk_mcp_server_name (server_name),
    KEY idx_mcp_server_status (status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='MCP Server 定义表';

CREATE TABLE IF NOT EXISTS mcp_tool_snapshot (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    snapshot_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '工具快照业务ID',
    server_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'MCP Server 业务ID',
    tool_key VARCHAR(64) NOT NULL DEFAULT '' COMMENT '映射到本系统的工具 key',
    original_tool_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'MCP 原始工具名',
    description TEXT NULL COMMENT '工具描述',
    input_schema_json JSON NULL COMMENT 'MCP inputSchema',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    last_synced_time DATETIME(3) NOT NULL COMMENT '最近同步时间',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mcp_tool_snapshot_uid (snapshot_uid),
    UNIQUE KEY uk_mcp_tool_key (tool_key),
    UNIQUE KEY uk_mcp_tool_server_name (server_uid, original_tool_name),
    KEY idx_mcp_tool_server (server_uid, status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='MCP Tool 快照表';

CREATE TABLE IF NOT EXISTS skill_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    skill_key VARCHAR(64) NOT NULL DEFAULT '' COMMENT '技能唯一标识',
    display_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '技能展示名称',
    description VARCHAR(512) NOT NULL DEFAULT '' COMMENT '技能描述',
    skill_path VARCHAR(512) NOT NULL DEFAULT '' COMMENT '技能路径',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    sort_index INT NOT NULL DEFAULT 0 COMMENT '排序值',
    config_json JSON NULL COMMENT '扩展配置',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_definition_key (skill_key),
    KEY idx_skill_definition_status (status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='技能定义表';

CREATE TABLE IF NOT EXISTS agent_skill_relation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    relation_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '关联业务ID',
    agent_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Agent 业务ID',
    skill_key VARCHAR(64) NOT NULL DEFAULT '' COMMENT '技能唯一标识',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    sort_index INT NOT NULL DEFAULT 0 COMMENT '排序值',
    config_json JSON NULL COMMENT '扩展配置',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_skill_relation_uid (relation_uid),
    UNIQUE KEY uk_agent_skill_relation_pair (agent_uid, skill_key),
    KEY idx_agent_skill_relation_agent (agent_uid, status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent 与技能关联表';

CREATE TABLE IF NOT EXISTS agent_group_member (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    member_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '群成员关系业务ID',
    agent_group_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '所属 Agent 群组业务ID',
    agent_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '所属 Agent 业务ID',
    member_role VARCHAR(64) NOT NULL DEFAULT '' COMMENT '群内角色，例如 owner / planner / executor / reviewer',
    responsibility VARCHAR(255) NOT NULL DEFAULT '' COMMENT '群内职责说明',
    sort_index INT NOT NULL DEFAULT 0 COMMENT '群内排序值，越小越靠前',
    is_primary TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否主 Agent：0否 1是',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_group_member_uid (member_uid),
    UNIQUE KEY uk_agent_group_member_relation (agent_group_uid, agent_uid),
    KEY idx_agent_group_member_group_sort (agent_group_uid, sort_index) COMMENT '按群组和排序查询成员',
    KEY idx_agent_group_member_agent (agent_uid) COMMENT '按 Agent 查询所在群组'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent 群组成员表';

CREATE TABLE IF NOT EXISTS agent_tip (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    tip_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '锦囊业务ID',
    agent_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Agent 业务ID',
    title VARCHAR(128) NOT NULL DEFAULT '' COMMENT '锦囊标题',
    summary VARCHAR(512) NOT NULL DEFAULT '' COMMENT '锦囊摘要',
    source_content TEXT NOT NULL COMMENT '锦囊原始内容',
    source_conversation_uid VARCHAR(64) NULL DEFAULT NULL COMMENT '来源对话ID',
    source_message_uid VARCHAR(64) NULL DEFAULT NULL COMMENT '来源消息ID',
    source_time DATETIME(3) NULL COMMENT '来源消息时间',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    sort_index INT NOT NULL DEFAULT 0 COMMENT '排序值',
    ext_config JSON NULL COMMENT '扩展配置',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_tip_uid (tip_uid),
    KEY idx_agent_tip_agent (agent_uid, status, updated_time),
    KEY idx_agent_tip_source_message (agent_uid, source_message_uid)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent 锦囊表';

CREATE TABLE IF NOT EXISTS agent_conversation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    conversation_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '对话业务ID',
    agent_group_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '归属 Agent 群组业务ID',
    agent_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '归属 Agent 业务ID',
    channel VARCHAR(32) NOT NULL DEFAULT 'web' COMMENT '会话来源渠道：web/feishu/dingtalk/noop',
    title VARCHAR(255) NOT NULL DEFAULT '' COMMENT '对话标题',
    pinned TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否置顶',
    input_tokens INT NOT NULL DEFAULT 0 COMMENT '对话累计输入 token 数',
    output_tokens INT NOT NULL DEFAULT 0 COMMENT '对话累计输出 token 数',
    total_tokens INT NOT NULL DEFAULT 0 COMMENT '对话累计总 token 数',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_conversation_uid (conversation_uid),
    KEY idx_agent_conversation_group_uid (agent_group_uid) COMMENT '按 Agent 群组查询对话',
    KEY idx_agent_conversation_agent_uid (agent_uid) COMMENT '按 Agent 查询对话'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='智能体对话表';

CREATE TABLE IF NOT EXISTS agent_channel_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    session_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '渠道会话业务ID',
    channel VARCHAR(32) NOT NULL DEFAULT '' COMMENT '渠道类型',
    tenant_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '租户或应用ID',
    session_key VARCHAR(255) NOT NULL DEFAULT '' COMMENT '平台会话键',
    conversation_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '绑定的对话ID',
    reply_target VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '回复目标地址',
    route_metadata JSON NULL COMMENT '路由元数据',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_channel_session_uid (session_uid),
    UNIQUE KEY uk_agent_channel_session_key (channel, tenant_id, session_key),
    KEY idx_agent_channel_session_conversation (conversation_uid)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='渠道会话映射表';

CREATE TABLE IF NOT EXISTS agent_channel_inbound_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    log_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '入站日志业务ID',
    channel VARCHAR(32) NOT NULL DEFAULT '' COMMENT '渠道类型',
    tenant_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '租户或应用ID',
    session_key VARCHAR(255) NOT NULL DEFAULT '' COMMENT '平台会话键',
    external_message_id VARCHAR(255) NOT NULL DEFAULT '' COMMENT '平台消息ID',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_channel_inbound_log_uid (log_uid),
    UNIQUE KEY uk_agent_channel_inbound_message (channel, tenant_id, external_message_id),
    KEY idx_agent_channel_inbound_session (channel, tenant_id, session_key)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='渠道入站幂等日志';

CREATE TABLE IF NOT EXISTS agent_cron_subscription (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    subscription_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '订阅业务ID',
    job_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '定时任务业务ID',
    channel VARCHAR(32) NOT NULL DEFAULT '' COMMENT '推送渠道',
    target VARCHAR(256) NOT NULL DEFAULT '' COMMENT '推送目标地址',
    bot_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '机器人ID，空表示使用默认机器人',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用订阅',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_cron_subscription_uid (subscription_uid),
    UNIQUE KEY uk_agent_cron_subscription_target (job_uid, channel, target, bot_id),
    KEY idx_agent_cron_subscription_job_enabled (job_uid, enabled)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='定时任务渠道订阅表';

CREATE TABLE IF NOT EXISTS agent_message (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    message_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '消息唯一标识（业务ID，全局唯一）',
    conversation_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '所属对话ID',
    parent_message_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '父消息ID，根消息为空字符串',
    role VARCHAR(16) NOT NULL DEFAULT '' COMMENT '消息角色：user / assistant',
    content LONGTEXT NOT NULL COMMENT '消息正文内容',
    status VARCHAR(32) NOT NULL DEFAULT '' COMMENT '消息状态：created / planned / running / replanning / waiting_approval / completed / failed / canceled',
    provider VARCHAR(32) NOT NULL DEFAULT '' COMMENT '模型服务商，如 openai / qwen / deepseek',
    model_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '具体模型名称，如 gpt-5.4 / qwen-max',
    input_tokens INT NOT NULL DEFAULT 0 COMMENT '输入 token 数',
    output_tokens INT NOT NULL DEFAULT 0 COMMENT '输出 token 数',
    total_tokens INT NOT NULL DEFAULT 0 COMMENT '总 token 数',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_message_uid (message_uid),
    KEY idx_agent_message_session_time (conversation_uid, created_time) COMMENT '按对话和时间查询消息',
    KEY idx_agent_message_parent (parent_message_uid) COMMENT '按父消息查询回复链',
    KEY idx_agent_message_session_status (conversation_uid, status) COMMENT '按对话和状态查询消息',
    KEY idx_agent_message_created_time (created_time) COMMENT '按时间范围查询消息'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='智能体消息表';

CREATE TABLE IF NOT EXISTS agent_message_attachment (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    upload_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '上传业务ID',
    conversation_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '所属对话ID',
    message_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '关联消息ID，空表示草稿上传',
    original_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '原始文件名',
    content_type VARCHAR(128) NOT NULL DEFAULT '' COMMENT '文件内容类型',
    mime_group VARCHAR(32) NOT NULL DEFAULT '' COMMENT '归一化文件分组',
    file_path VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '本地存储路径',
    file_url VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '对外访问地址',
    size_bytes BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小',
    previewable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否可直接预览',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_message_attachment_upload_uid (upload_uid),
    KEY idx_agent_message_attachment_conversation (conversation_uid, status, created_time),
    KEY idx_agent_message_attachment_message (message_uid, status, created_time)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='对话消息附件表';

CREATE TABLE IF NOT EXISTS agent_step (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    step_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '步骤业务ID',
    message_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '关联的用户消息ID',
    conversation_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '所属对话ID',
    round_index INT NOT NULL DEFAULT 1 COMMENT '所属循环轮次',
    step_index INT NOT NULL DEFAULT 1 COMMENT '轮次内步骤序号',
    title VARCHAR(255) NOT NULL DEFAULT '' COMMENT '步骤标题',
    tool_name VARCHAR(64) NOT NULL DEFAULT '' COMMENT '工具名称',
    tool_args JSON NOT NULL COMMENT '工具参数JSON',
    done_criteria VARCHAR(512) NOT NULL DEFAULT '' COMMENT '完成标准文本',
    risk_level VARCHAR(16) NOT NULL DEFAULT '' COMMENT '风险级别：LOW / HIGH',
    status VARCHAR(32) NOT NULL DEFAULT '' COMMENT '步骤状态',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    approval_status VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '审批状态',
    last_error TEXT NULL COMMENT '最后一次错误信息',
    output_text LONGTEXT NULL COMMENT '步骤最终输出文本',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_step_uid (step_uid),
    KEY idx_agent_step_message (message_uid) COMMENT '按消息查询步骤',
    KEY idx_agent_step_session (conversation_uid) COMMENT '按对话查询步骤'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='智能体步骤执行表';

CREATE TABLE IF NOT EXISTS agent_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    event_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '事件业务ID',
    conversation_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '所属对话ID',
    message_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '关联的用户消息ID，为空表示对话级事件',
    step_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '关联的步骤ID，为空表示非步骤事件',
    event_type VARCHAR(64) NOT NULL DEFAULT '' COMMENT '事件类型',
    payload JSON NULL COMMENT '事件载荷JSON',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_event_uid (event_uid),
    KEY idx_agent_event_session (conversation_uid) COMMENT '按对话查询事件',
    KEY idx_agent_event_message (message_uid) COMMENT '按消息查询事件'
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='智能体事件审计表';

CREATE TABLE IF NOT EXISTS system_error_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    log_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '日志业务ID',
    level VARCHAR(16) NOT NULL DEFAULT 'ERROR' COMMENT '级别：ERROR / WARN',
    source VARCHAR(64) NOT NULL DEFAULT '' COMMENT '来源模块',
    code VARCHAR(128) NOT NULL DEFAULT '' COMMENT '错误代码',
    title VARCHAR(255) NOT NULL DEFAULT '' COMMENT '错误标题',
    message TEXT NULL COMMENT '错误摘要',
    detail LONGTEXT NULL COMMENT '错误详情',
    occurred_time DATETIME(3) NOT NULL COMMENT '发生时间',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_error_log_uid (log_uid),
    KEY idx_system_error_log_time (occurred_time DESC),
    KEY idx_system_error_log_level_time (level, occurred_time DESC),
    KEY idx_system_error_log_source_time (source, occurred_time DESC)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='系统关键错误日志表';

CREATE TABLE IF NOT EXISTS agent_cron_job (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    job_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '任务业务ID',
    agent_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Agent 业务ID',
    conversation_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '来源对话ID',
    message_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '来源消息ID',
    title VARCHAR(255) NOT NULL DEFAULT '' COMMENT '任务标题',
    expression VARCHAR(128) NOT NULL DEFAULT '' COMMENT '标准化 cron 表达式',
    timezone VARCHAR(64) NOT NULL DEFAULT '' COMMENT '时区',
    task_content VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '任务内容',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED / DELETED',
    last_run_time DATETIME(3) NULL COMMENT '最近执行时间',
    next_run_time DATETIME(3) NULL COMMENT '下次执行时间',
    last_result TEXT NULL COMMENT '最近一次执行结果',
    ext_config JSON NULL COMMENT '扩展配置',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_cron_job_uid (job_uid),
    KEY idx_agent_cron_job_agent (agent_uid, status),
    KEY idx_agent_cron_job_next_run (status, next_run_time)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent 定时任务表';

INSERT INTO agent_definition (
    agent_uid, agent_name, display_name, avatar, description, capability_tags, prompt_profile,
    model_provider_id, model_id, sort_index, is_group_entry, status, workspace, ext_config, created_time, updated_time
) VALUES
    ('agent_general_assistant', 'default_agent', '默认助手', '🤝', '负责综合规划、协调执行与最终总结。', JSON_ARRAY('planning', 'coordination', 'delivery'),
     'generalist', '', '', 10, 0, 'ACTIVE', '', JSON_OBJECT(), NOW(3), NOW(3)),
    ('agent_test_expert', 'test_expert', '测试专家', '🧪', '负责测试设计、缺陷定位与质量把关。', JSON_ARRAY('testing', 'qa', 'review'),
     'qa-specialist', '', '', 20, 0, 'ACTIVE', '', JSON_OBJECT(), NOW(3), NOW(3)),
    ('agent_public_opinion', 'public_opinion_monitor', '舆情监测', '📡', '负责舆情跟踪、热点观察与风险提示。', JSON_ARRAY('monitoring', 'trend', 'risk'),
     'opinion-specialist', '', '', 30, 0, 'ACTIVE', '', JSON_OBJECT(), NOW(3), NOW(3)),
    ('agent_marketing_assistant', 'marketing_assistant', '营销助理', '📣', '负责传播文案、活动建议与投放辅助。', JSON_ARRAY('marketing', 'campaign', 'copywriting'),
     'marketing-specialist', '', '', 40, 0, 'ACTIVE', '', JSON_OBJECT(), NOW(3), NOW(3));

INSERT INTO tool_definition (
    tool_key, display_name, description, risk_level, status, sort_index, config_json, created_time, updated_time
) VALUES
    ('CommandTool', '命令执行', 'Execute a local shell command on the current machine.', 'HIGH', 'ACTIVE', 10, JSON_OBJECT(), NOW(3), NOW(3)),
    ('BrowserTool', '浏览器工具', 'Operate a browser page to open URLs, click, type, extract text, or take screenshots.', 'HIGH', 'ACTIVE', 20, JSON_OBJECT(), NOW(3), NOW(3)),
    ('FileTool', '文件工具', 'Read, write, append, edit, or list local files.', 'HIGH', 'ACTIVE', 40, JSON_OBJECT(), NOW(3), NOW(3)),
    ('CronCreateTool', '创建定时任务', 'Create a scheduled cron-like automation task.', 'HIGH', 'ACTIVE', 60, JSON_OBJECT(), NOW(3), NOW(3)),
    ('CronDeleteTool', '删除定时任务', 'Delete a scheduled cron-like automation task.', 'HIGH', 'ACTIVE', 61, JSON_OBJECT(), NOW(3), NOW(3)),
    ('CronListTool', '查询定时任务', 'List or query scheduled cron-like automation tasks.', 'HIGH', 'ACTIVE', 62, JSON_OBJECT(), NOW(3), NOW(3)),
    ('FileSearchTool', '文件搜索', 'Search files by text grep or glob pattern.', 'LOW', 'ACTIVE', 70, JSON_OBJECT(), NOW(3), NOW(3)),
    ('DesktopScreenshotTool', '桌面截图', 'Capture a desktop screenshot on the local machine.', 'LOW', 'ACTIVE', 80, JSON_OBJECT(), NOW(3), NOW(3)),
    ('CurrentTimeTool', '当前时间', 'Get the current UTC time.', 'LOW', 'ACTIVE', 90, JSON_OBJECT(), NOW(3), NOW(3)),
    ('TokenUsageTool', 'Token 使用', 'Query stored token usage summary from message records.', 'LOW', 'ACTIVE', 100, JSON_OBJECT(), NOW(3), NOW(3)),
    ('MemorySearchTool', '记忆搜索', 'Search historical conversation messages by keyword.', 'LOW', 'ACTIVE', 110, JSON_OBJECT(), NOW(3), NOW(3)),
    ('ImageLoaderTool', '图片加载', 'On-demand image context loader for visual analysis after tools that return image paths; supports 刚才截图/第N轮截图/文件名 and direct HTTP(S) image URL passthrough without local download.', 'LOW', 'ACTIVE', 115, JSON_OBJECT(), NOW(3), NOW(3));

INSERT INTO skill_definition (
    skill_key, display_name, description, skill_path, status, sort_index, config_json, created_time, updated_time
) VALUES
    ('pdf', 'pdf', 'Use this skill whenever the task involves reading or generating PDFs.', 'skills/pdf', 'ACTIVE', 10, JSON_OBJECT(), NOW(3), NOW(3)),
    ('xlsx', 'xlsx', 'Use this skill whenever the task involves spreadsheet files (.xlsx/.xls/.csv/.tsv).', 'skills/xlsx', 'ACTIVE', 20, JSON_OBJECT(), NOW(3), NOW(3)),
    ('pptx', 'pptx', 'Use this skill whenever the task involves creating or editing PowerPoint presentations.', 'skills/pptx', 'ACTIVE', 30, JSON_OBJECT(), NOW(3), NOW(3)),
    ('docx', 'docx', 'Use this skill whenever the task involves creating or editing Word documents with formatting fidelity.', 'skills/docx', 'ACTIVE', 40, JSON_OBJECT(), NOW(3), NOW(3));

INSERT INTO agent_tool_relation (
    relation_uid, agent_uid, tool_key, status, sort_index, config_json, created_time, updated_time
) VALUES
    ('RelGeneralCommand', 'agent_general_assistant', 'CommandTool', 'ACTIVE', 10, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelGeneralBrowser', 'agent_general_assistant', 'BrowserTool', 'ACTIVE', 20, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelGeneralFile', 'agent_general_assistant', 'FileTool', 'ACTIVE', 40, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelGeneralCronCreate', 'agent_general_assistant', 'CronCreateTool', 'ACTIVE', 60, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelGeneralCronDelete', 'agent_general_assistant', 'CronDeleteTool', 'ACTIVE', 61, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelGeneralCronList', 'agent_general_assistant', 'CronListTool', 'ACTIVE', 62, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelGeneralSearch', 'agent_general_assistant', 'FileSearchTool', 'ACTIVE', 70, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelGeneralShot', 'agent_general_assistant', 'DesktopScreenshotTool', 'ACTIVE', 80, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelGeneralTime', 'agent_general_assistant', 'CurrentTimeTool', 'ACTIVE', 90, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelGeneralToken', 'agent_general_assistant', 'TokenUsageTool', 'ACTIVE', 100, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelGeneralImageLoader', 'agent_general_assistant', 'ImageLoaderTool', 'ACTIVE', 115, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelGeneralMemory', 'agent_general_assistant', 'MemorySearchTool', 'ACTIVE', 110, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelTestCommand', 'agent_test_expert', 'CommandTool', 'ACTIVE', 10, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelTestFile', 'agent_test_expert', 'FileTool', 'ACTIVE', 20, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelTestSearch', 'agent_test_expert', 'FileSearchTool', 'ACTIVE', 30, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelTestTime', 'agent_test_expert', 'CurrentTimeTool', 'ACTIVE', 40, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelTestMemory', 'agent_test_expert', 'MemorySearchTool', 'ACTIVE', 50, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelPublicBrowser', 'agent_public_opinion', 'BrowserTool', 'ACTIVE', 10, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelPublicSearch', 'agent_public_opinion', 'FileSearchTool', 'ACTIVE', 30, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelPublicTime', 'agent_public_opinion', 'CurrentTimeTool', 'ACTIVE', 40, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelPublicMemory', 'agent_public_opinion', 'MemorySearchTool', 'ACTIVE', 50, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelMarketCommand', 'agent_marketing_assistant', 'CommandTool', 'ACTIVE', 10, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelMarketBrowser', 'agent_marketing_assistant', 'BrowserTool', 'ACTIVE', 20, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelMarketSearch', 'agent_marketing_assistant', 'FileSearchTool', 'ACTIVE', 30, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelMarketTime', 'agent_marketing_assistant', 'CurrentTimeTool', 'ACTIVE', 40, JSON_OBJECT(), NOW(3), NOW(3)),
    ('RelMarketMemory', 'agent_marketing_assistant', 'MemorySearchTool', 'ACTIVE', 50, JSON_OBJECT(), NOW(3), NOW(3));

INSERT INTO agent_skill_relation (
    relation_uid, agent_uid, skill_key, status, sort_index, config_json, created_time, updated_time
) VALUES
    ('rel_general_pdf', 'agent_general_assistant', 'pdf', 'ACTIVE', 10, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_general_xlsx', 'agent_general_assistant', 'xlsx', 'ACTIVE', 20, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_general_pptx', 'agent_general_assistant', 'pptx', 'ACTIVE', 30, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_general_docx', 'agent_general_assistant', 'docx', 'ACTIVE', 40, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_test_pdf', 'agent_test_expert', 'pdf', 'ACTIVE', 10, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_test_xlsx', 'agent_test_expert', 'xlsx', 'ACTIVE', 20, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_test_pptx', 'agent_test_expert', 'pptx', 'ACTIVE', 30, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_test_docx', 'agent_test_expert', 'docx', 'ACTIVE', 40, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_public_pdf', 'agent_public_opinion', 'pdf', 'ACTIVE', 10, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_public_xlsx', 'agent_public_opinion', 'xlsx', 'ACTIVE', 20, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_public_pptx', 'agent_public_opinion', 'pptx', 'ACTIVE', 30, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_public_docx', 'agent_public_opinion', 'docx', 'ACTIVE', 40, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_market_pdf', 'agent_marketing_assistant', 'pdf', 'ACTIVE', 10, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_market_xlsx', 'agent_marketing_assistant', 'xlsx', 'ACTIVE', 20, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_market_pptx', 'agent_marketing_assistant', 'pptx', 'ACTIVE', 30, JSON_OBJECT(), NOW(3), NOW(3)),
    ('rel_market_docx', 'agent_marketing_assistant', 'docx', 'ACTIVE', 40, JSON_OBJECT(), NOW(3), NOW(3));

INSERT INTO agent_tip (
    tip_uid, agent_uid, title, summary, source_content, source_conversation_uid, source_message_uid,
    source_time, status, sort_index, ext_config, created_time, updated_time
) VALUES
    ('tip_general_weather_brief', 'agent_general_assistant', '天气查询汇报（示例）',
     '先查今天天气，再用 3 句话汇报：天气现状、体感建议、是否需要带伞。',
     '请查询今天北京天气，并给我一段简洁汇报：天气、温度、出行建议。',
     NULL, NULL, NOW(3), 'ACTIVE', 10, JSON_OBJECT(), NOW(3), NOW(3)),
    ('tip_general_meeting_minutes', 'agent_general_assistant', '会议纪要整理',
     '按“结论、待办、负责人、截止时间”四段输出，先结论后细节。',
     '把会议录音整理成纪要，并提取可执行待办。',
     NULL, NULL, NOW(3), 'ACTIVE', 20, JSON_OBJECT(), NOW(3), NOW(3)),
    ('tip_general_weekly_report', 'agent_general_assistant', '周报生成',
     '分成“本周完成、下周计划、风险阻塞”三块，每块不超过 3 条。',
     '根据本周工作记录，写一份简洁周报。',
     NULL, NULL, NOW(3), 'ACTIVE', 30, JSON_OBJECT(), NOW(3), NOW(3)),
    ('tip_general_resume_screening', 'agent_general_assistant', '简历初筛',
     '先给匹配分，再写风险项，最后给“推荐/备选/不推荐”的结论。',
     '请筛选候选人简历并给出推荐意见。',
     NULL, NULL, NOW(3), 'ACTIVE', 40, JSON_OBJECT(), NOW(3), NOW(3)),
    ('tip_general_competitor_analysis', 'agent_general_assistant', '竞品分析速览',
     '按“产品定位、优势、短板、可借鉴点”输出，避免长篇描述。',
     '对比三个竞品并给出结论。',
     NULL, NULL, NOW(3), 'ACTIVE', 50, JSON_OBJECT(), NOW(3), NOW(3)),
    ('tip_general_email_rewrite', 'agent_general_assistant', '商务邮件润色',
     '保持礼貌和直接，先目的后请求，最后附明确下一步。',
     '帮我把这封商务邮件改得更专业。',
     NULL, NULL, NOW(3), 'ACTIVE', 60, JSON_OBJECT(), NOW(3), NOW(3)),
    ('tip_general_sop_draft', 'agent_general_assistant', 'SOP 草案',
     '按“目标、步骤、检查项、异常处理”结构写，便于落地执行。',
     '给这个流程写一份标准操作说明。',
     NULL, NULL, NOW(3), 'ACTIVE', 70, JSON_OBJECT(), NOW(3), NOW(3)),
    ('tip_general_customer_reply', 'agent_general_assistant', '客户回复模板',
     '先共情，再给方案，最后确认时间点，控制在 120 字内。',
     '帮我回复客户投诉邮件。',
     NULL, NULL, NOW(3), 'ACTIVE', 80, JSON_OBJECT(), NOW(3), NOW(3)),
    ('tip_general_content_outline', 'agent_general_assistant', '内容大纲生成',
     '先给 5 条一级标题，再给每条 2-3 个要点，结构清晰即可。',
     '帮我写一篇文章的大纲。',
     NULL, NULL, NOW(3), 'ACTIVE', 90, JSON_OBJECT(), NOW(3), NOW(3)),
    ('tip_general_risk_checklist', 'agent_general_assistant', '风险排查清单',
     '按“高/中/低风险”分组列出，并给每项一个可执行规避建议。',
     '帮我排查这个方案的风险。',
     NULL, NULL, NOW(3), 'ACTIVE', 100, JSON_OBJECT(), NOW(3), NOW(3));

CREATE TABLE IF NOT EXISTS llm_provider_config (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    provider_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Provider 业务ID',
    provider_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Provider 展示名称',
    protocol VARCHAR(64) NOT NULL DEFAULT '' COMMENT '协议类型',
    base_url VARCHAR(512) NOT NULL DEFAULT '' COMMENT '基础请求地址',
    api_key_ciphertext TEXT NOT NULL COMMENT '加密后的 API Key',
    default_model VARCHAR(128) NOT NULL DEFAULT '' COMMENT '默认模型 ID',
    local TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否本地模型服务',
    require_api_key TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否要求 API Key',
    freeze_url TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否固定 URL',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    ext_config JSON NULL COMMENT '扩展配置JSON',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_llm_provider_config_provider_id (provider_id),
    KEY idx_llm_provider_config_status (status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='LLM Provider 配置表';

CREATE TABLE IF NOT EXISTS llm_provider_model (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    provider_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Provider 业务ID',
    model_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '模型唯一标识',
    model_name VARCHAR(255) NOT NULL DEFAULT '' COMMENT '模型展示名称',
    capabilities_json JSON NOT NULL COMMENT '能力列表 JSON 数组',
    reasoning TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否支持 reasoning/thinking',
    context_window INT NOT NULL DEFAULT 0 COMMENT '上下文窗口大小，0 表示未知',
    max_input_tokens INT NOT NULL DEFAULT 0 COMMENT '最大输入 token，0 表示未知',
    max_output_tokens INT NOT NULL DEFAULT 0 COMMENT '最大输出 token，0 表示未知',
    upload_policy_json JSON NULL COMMENT '上传策略配置',
    sort_index INT NOT NULL DEFAULT 0 COMMENT '排序值',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
    created_time DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_llm_provider_model_pair (provider_id, model_id),
    KEY idx_llm_provider_model_provider (provider_id, status, sort_index)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='LLM Provider 模型表';
