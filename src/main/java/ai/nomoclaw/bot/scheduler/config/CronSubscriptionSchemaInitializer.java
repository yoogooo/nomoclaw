package ai.nomoclaw.bot.scheduler.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
public class CronSubscriptionSchemaInitializer {

    private final DataSource dataSource;

    public CronSubscriptionSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initializeIfNeeded() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS agent_cron_subscription (
                        id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
                        subscription_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '订阅业务ID',
                        job_uid VARCHAR(64) NOT NULL DEFAULT '' COMMENT '定时任务业务ID',
                        channel VARCHAR(32) NOT NULL DEFAULT '' COMMENT '推送渠道',
                        target VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '推送目标地址',
                        enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用订阅',
                        created_time DATETIME(3) NOT NULL COMMENT '创建时间',
                        updated_time DATETIME(3) NOT NULL COMMENT '更新时间',
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_agent_cron_subscription_uid (subscription_uid),
                        UNIQUE KEY uk_agent_cron_subscription_target (job_uid, channel, target),
                        KEY idx_agent_cron_subscription_job_enabled (job_uid, enabled)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                    """);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize agent_cron_subscription schema", ex);
        }
    }
}
