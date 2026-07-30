package ai.nomoclaw.bot.knowledge;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;

import javax.sql.DataSource;

/** Database helpers for tests that need the real knowledge schema. */
public final class TestDatabaseSupport {

    private TestDatabaseSupport() {
    }

    /** Applies the knowledge H2 schema from the shared db-schema module. */
    public static void migrateKnowledgeSchema(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/h2")
                .target(MigrationVersion.fromVersion("8"))
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
    }
}
