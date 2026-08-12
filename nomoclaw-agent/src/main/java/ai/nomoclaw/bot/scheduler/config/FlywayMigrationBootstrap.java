package ai.nomoclaw.bot.scheduler.config;

import ai.nomoclaw.bot.orchestrator.SystemErrorLogService;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Arrays;

@Component
@DependsOn({"h2SchemaInitializer"})
@Slf4j
public class FlywayMigrationBootstrap implements ApplicationRunner {

    private final ObjectProvider<Flyway> flywayProvider;
    private final DataSource dataSource;
    private final Environment environment;
    private final SystemErrorLogService systemErrorLogService;

    public FlywayMigrationBootstrap(ObjectProvider<Flyway> flywayProvider,
                                    DataSource dataSource,
                                    Environment environment,
                                    SystemErrorLogService systemErrorLogService) {
        this.flywayProvider = flywayProvider;
        this.dataSource = dataSource;
        this.environment = environment;
        this.systemErrorLogService = systemErrorLogService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String databaseProduct = detectDatabaseProduct();
        if (databaseProduct.contains("sqlite")) {
            log.info("[Flyway] SQLite schema is maintained by the embedded schema initializer");
            return;
        }
        boolean useProgrammaticFlyway = false;
        Flyway flyway = flywayProvider.getIfAvailable();
        if (flyway == null) {
            flyway = buildProgrammaticFlyway(databaseProduct);
            useProgrammaticFlyway = true;
            log.warn("[Flyway] Flyway bean not found, fallback to programmatic migrate locations={}",
                    Arrays.toString(resolveLocations(databaseProduct)));
        }

        try {
            MigrateResult result = flyway.migrate();
            log.info("[Flyway] migrate finished database={} programmatic={} result={}",
                    databaseProduct, useProgrammaticFlyway, result);
        } catch (Exception ex) {
            systemErrorLogService.recordException(
                    "ERROR",
                    "Flyway",
                    "FLYWAY_MIGRATE_FAILED",
                    "Flyway 迁移失败",
                    "schema 自动升级已跳过，应用继续启动。",
                    ex
            );
            if (failOnMigrateError()) {
                throw new IllegalStateException("flyway migrate failed, schema auto-upgrade is unavailable", ex);
            }
            log.warn("[Flyway] migrate failed, schema auto-upgrade skipped database={} programmatic={} err={}",
                    databaseProduct, useProgrammaticFlyway, ex.toString(), ex);
        }
    }

    private boolean failOnMigrateError() {
        return environment.getProperty("nomoclaw.flyway.fail-on-migrate-error", Boolean.class, false);
    }

    private Flyway buildProgrammaticFlyway(String databaseProduct) {
        boolean baselineOnMigrate = environment.getProperty("spring.flyway.baseline-on-migrate", Boolean.class, true);
        String baselineVersion = environment.getProperty("spring.flyway.baseline-version", "0");
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations(resolveLocations(databaseProduct))
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(MigrationVersion.fromVersion(baselineVersion));
        return configuration.load();
    }

    private String[] resolveLocations(String databaseProduct) {
        String configured = environment.getProperty("spring.flyway.locations", "");
        if (configured != null && !configured.isBlank()) {
            return Arrays.stream(configured.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toArray(String[]::new);
        }
        if (databaseProduct.contains("mysql")) {
            return new String[]{"classpath:db/migration/mysql"};
        }
        if (databaseProduct.contains("h2")) {
            return new String[]{"classpath:db/migration/h2"};
        }
        return new String[]{"classpath:db/migration"};
    }

    private String detectDatabaseProduct() {
        try (Connection connection = dataSource.getConnection()) {
            String databaseProductName = connection.getMetaData().getDatabaseProductName();
            return databaseProductName == null ? "" : databaseProductName.trim().toLowerCase();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to detect database product for flyway migration", ex);
        }
    }
}
