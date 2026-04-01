package ai.nomoclaw.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@ConfigurationPropertiesScan(basePackages = {"ai.nomoclaw.bot"})
@MapperScan({"ai.nomoclaw.bot.**.mapper"})
public class NomoClawApplication {

	public static void main(String[] args) {
		SpringApplication.run(NomoClawApplication.class, args);
	}

}
