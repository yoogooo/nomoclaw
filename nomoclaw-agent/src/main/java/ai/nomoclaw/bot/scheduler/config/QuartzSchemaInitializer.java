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
public class QuartzSchemaInitializer {

    private static final String MYSQL_SCHEMA_RESOURCE = "org/quartz/impl/jdbcjobstore/tables_mysql_innodb.sql";
    private static final String H2_SCHEMA_RESOURCE = "org/quartz/impl/jdbcjobstore/tables_h2.sql";

    private final DataSource dataSource;

    public QuartzSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initializeIfNeeded() {
        try (Connection connection = dataSource.getConnection()) {
            String databaseProductName = connection.getMetaData().getDatabaseProductName();
            String schemaResource = quartzSchemaResource(databaseProductName);
            if (schemaResource == null) {
                log.info("[Quartz] skip schema bootstrap for database={}", databaseProductName);
                return;
            }
            if (hasQuartzTables(connection)) {
                log.info("[Quartz] schema already initialized");
                return;
            }
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(schemaResource));
            populator.setContinueOnError(false);
            populator.setIgnoreFailedDrops(true);
            populator.setCommentPrefixes("#", "--");
            populator.setSeparator(";");
            populator.execute(dataSource);
            log.info("[Quartz] schema initialized from {}", schemaResource);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize Quartz schema", ex);
        }
    }

    private String quartzSchemaResource(String databaseProductName) {
        if (databaseProductName == null) {
            return null;
        }
        String normalized = databaseProductName.toLowerCase();
        if (normalized.contains("mysql")) {
            return MYSQL_SCHEMA_RESOURCE;
        }
        if (normalized.contains("h2")) {
            return H2_SCHEMA_RESOURCE;
        }
        return null;
    }

    private boolean hasQuartzTables(Connection connection) throws Exception {
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, "QRTZ_JOB_DETAILS", null)) {
            if (tables.next()) {
                return true;
            }
        }
        try (ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), null, "qrtz_job_details", null)) {
            return tables.next();
        }
    }
}
