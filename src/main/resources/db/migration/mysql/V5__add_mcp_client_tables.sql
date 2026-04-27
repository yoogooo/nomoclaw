CREATE TABLE IF NOT EXISTS mcp_server_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    server_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'MCP Server 业务ID',
    server_name VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'MCP Server 唯一名称',
    display_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '展示名称',
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
    display_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '展示名称',
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
