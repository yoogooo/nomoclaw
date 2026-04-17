package ai.nomoclaw.bot.scheduler.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Component
@DependsOn({"h2SchemaInitializer"})
@Slf4j
public class ConversationSchemaUpgrader {

    private final DataSource dataSource;

    public ConversationSchemaUpgrader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void upgradeIfNeeded() {
        try (Connection connection = dataSource.getConnection()) {
            if (!hasTable(connection, "agent_conversation")) {
                log.info("[ConversationSchema] skip upgrade, table agent_conversation not found");
                return;
            }
            if (hasColumn(connection, "agent_conversation", "pinned")) {
                log.info("[ConversationSchema] column pinned already exists");
                return;
            }

            String databaseProductName = connection.getMetaData().getDatabaseProductName();
            String normalized = databaseProductName == null ? "" : databaseProductName.toLowerCase();
            String ddl;
            if (normalized.contains("mysql")) {
                ddl = "ALTER TABLE agent_conversation ADD COLUMN pinned TINYINT(1) NOT NULL DEFAULT 0";
            } else if (normalized.contains("h2")) {
                ddl = "ALTER TABLE agent_conversation ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE";
            } else {
                log.info("[ConversationSchema] skip upgrade for database={}", databaseProductName);
                return;
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute(ddl);
                log.info("[ConversationSchema] upgraded table agent_conversation with pinned column");
            }
        } catch (Exception ex) {
            throw new IllegalStateException("failed to upgrade conversation schema", ex);
        }
    }

    private boolean hasTable(Connection connection, String tableName) throws Exception {
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, tableName, null)) {
            if (tables.next()) {
                return true;
            }
        }
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, tableName.toUpperCase(), null)) {
            if (tables.next()) {
                return true;
            }
        }
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, tableName.toLowerCase(), null)) {
            return tables.next();
        }
    }

    private boolean hasColumn(Connection connection, String tableName, String columnName) throws Exception {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName, columnName)) {
            if (columns.next()) {
                return true;
            }
        }
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName.toUpperCase(), columnName.toUpperCase())) {
            if (columns.next()) {
                return true;
            }
        }
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName.toLowerCase(), columnName.toLowerCase())) {
            return columns.next();
        }
    }
}
