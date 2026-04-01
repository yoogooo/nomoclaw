package ai.nomoclaw.bot.scheduler.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
@Slf4j
public class ConversationSchemaInitializer {

    private final DataSource dataSource;

    public ConversationSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initializeIfNeeded() {
        try (Connection connection = dataSource.getConnection()) {
            dropConversationRuntimeModelColumns(connection);
            ensureMessageAttachmentTable(connection);
            ensureUploadPolicyColumn(connection);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize conversation schema", ex);
        }
    }

    private void dropConversationRuntimeModelColumns(Connection connection) throws Exception {
        if (!hasTable(connection, "agent_conversation")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            if (!hasColumn(connection, "agent_conversation", "runtime_model_provider")) {
                return;
            }
            if (hasColumn(connection, "agent_conversation", "runtime_model_name")) {
                statement.execute("""
                        ALTER TABLE agent_conversation
                        DROP COLUMN runtime_model_name
                        """);
            }
            statement.execute("""
                    ALTER TABLE agent_conversation
                    DROP COLUMN runtime_model_provider
                    """);
        }
    }

    private void ensureMessageAttachmentTable(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
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
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                    """);
        }
    }

    private void ensureUploadPolicyColumn(Connection connection) throws Exception {
        if (!hasTable(connection, "llm_provider_model") || hasColumn(connection, "llm_provider_model", "upload_policy_json")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    ALTER TABLE llm_provider_model
                    ADD COLUMN upload_policy_json JSON NULL COMMENT '上传策略配置' AFTER max_output_tokens
                    """);
            log.info("[ConversationSchema] schema updated with upload_policy_json column");
        }
    }

    private boolean hasTable(Connection connection, String tableName) throws Exception {
        try (var tables = connection.getMetaData().getTables(connection.getCatalog(), null, tableName, null)) {
            if (tables.next()) {
                return true;
            }
        }
        try (var tables = connection.getMetaData().getTables(connection.getCatalog(), null, tableName.toUpperCase(), null)) {
            return tables.next();
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws Exception {
        try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName, columnName)) {
            if (columns.next()) {
                return true;
            }
        }
        try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName.toUpperCase(), columnName.toUpperCase())) {
            return columns.next();
        }
    }
}
