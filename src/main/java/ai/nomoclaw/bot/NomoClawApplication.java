package ai.nomoclaw.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@ConfigurationPropertiesScan(basePackages = {"ai.nomoclaw.bot"})
@MapperScan({"ai.nomoclaw.bot.**.mapper"})
public class NomoClawApplication {
	private static final Logger log = LoggerFactory.getLogger(NomoClawApplication.class);
	private static final long PROCESS_BOOT_NANOS = System.nanoTime();

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(NomoClawApplication.class);
		application.addListeners(event -> {
			if (event instanceof ApplicationStartedEvent) {
				logStartupTiming("application.started");
			} else if (event instanceof ApplicationReadyEvent) {
				logStartupTiming("application.ready");
			}
		});
		application.run(args);
	}

	private static void logStartupTiming(String phase) {
		long elapsedMs = (System.nanoTime() - PROCESS_BOOT_NANOS) / 1_000_000;
		log.info("[StartupTiming] phase={} elapsedMs={}", phase, elapsedMs);
	}
}
