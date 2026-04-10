package ai.nomoclaw.bot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebEntryConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/nomoclaw").setViewName("forward:/nomoclaw/index.html");
        registry.addViewController("/nomoclaw/").setViewName("forward:/nomoclaw/index.html");
    }
}
