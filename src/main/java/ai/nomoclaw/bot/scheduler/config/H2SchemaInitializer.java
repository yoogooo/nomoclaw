package ai.nomoclaw.bot.scheduler.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

@Component
@Slf4j
public class H2SchemaInitializer {

    private static final String H2_SCHEMA_RESOURCE = "db/schema-h2.sql";

    private final DataSource dataSource;

    public H2SchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initializeIfNeeded() {
        try (Connection connection = dataSource.getConnection()) {
            String databaseProductName = connection.getMetaData().getDatabaseProductName();
            if (databaseProductName == null || !databaseProductName.toLowerCase().contains("h2")) {
                return;
            }
            if (hasTable(connection, "agent_definition")) {
                log.info("[H2Schema] schema already initialized");
                return;
            }
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(H2_SCHEMA_RESOURCE));
            populator.setContinueOnError(false);
            populator.setIgnoreFailedDrops(true);
            populator.setCommentPrefixes("#", "--");
            populator.setSeparator(";");
            populator.execute(dataSource);
            log.info("[H2Schema] schema initialized from {}", H2_SCHEMA_RESOURCE);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize H2 schema", ex);
        }
    }

    private boolean hasTable(Connection connection, String tableName) throws Exception {
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, tableName, null)) {
            if (tables.next()) {
                return true;
            }
        }
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, tableName.toUpperCase(), null)) {
            return tables.next();
        }
    }
}
