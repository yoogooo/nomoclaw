package ai.nomoclaw.bot.scheduler.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
@Slf4j
public class CronJobSchemaInitializer {

    private final DataSource dataSource;

    public CronJobSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initializeIfNeeded() {
        try (Connection connection = dataSource.getConnection()) {
            if (!hasAgentCronJobTable(connection)) {
                return;
            }
            if (!hasTitleColumn(connection)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("""
                            ALTER TABLE agent_cron_job
                            ADD COLUMN title VARCHAR(255) NOT NULL DEFAULT ''
                            """);
                    statement.execute("""
                            UPDATE agent_cron_job
                            SET title = SUBSTRING(task_content, 1, 255)
                            WHERE title = ''
                            """);
                    log.info("[CronJob] schema updated with title column");
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize agent_cron_job schema", ex);
        }
    }

    private boolean hasAgentCronJobTable(Connection connection) throws Exception {
        try (var tables = connection.getMetaData().getTables(connection.getCatalog(), null, "agent_cron_job", null)) {
            if (tables.next()) {
                return true;
            }
        }
        try (var tables = connection.getMetaData().getTables(connection.getCatalog(), null, "AGENT_CRON_JOB", null)) {
            return tables.next();
        }
    }

    private boolean hasTitleColumn(Connection connection) throws Exception {
        try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "agent_cron_job", "title")) {
            if (columns.next()) {
                return true;
            }
        }
        try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "AGENT_CRON_JOB", "TITLE")) {
            return columns.next();
        }
    }
}
