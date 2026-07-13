package ai.nomoclaw.bot.scheduler.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
public class QuartzConfig {

    @Bean
    @Lazy
    public SpringBeanJobFactory quartzJobFactory(AutowireCapableBeanFactory beanFactory) {
        return new SpringBeanJobFactory() {
            @Override
            protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
                Object job = super.createJobInstance(bundle);
                beanFactory.autowireBean(job);
                return job;
            }
        };
    }

    @Bean
    @Lazy
    public SchedulerFactoryBean schedulerFactoryBean(DataSource dataSource,
                                                     SpringBeanJobFactory quartzJobFactory,
                                                     Environment environment) {
        SchedulerFactoryBean factoryBean = new SchedulerFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setJobFactory(quartzJobFactory);
        Properties properties = new Properties();
        copyQuartzProperty(environment, properties, "org.quartz.scheduler.instanceName");
        copyQuartzProperty(environment, properties, "org.quartz.scheduler.instanceId");
        copyQuartzProperty(environment, properties, "org.quartz.jobStore.isClustered");
        copyQuartzProperty(environment, properties, "org.quartz.jobStore.misfireThreshold");
        copyQuartzProperty(environment, properties, "org.quartz.threadPool.threadCount");
        if (!properties.containsKey("org.quartz.threadPool.threadCount")) {
            properties.setProperty("org.quartz.threadPool.threadCount", "3");
        }
        factoryBean.setQuartzProperties(properties);
        factoryBean.setWaitForJobsToCompleteOnShutdown(true);
        int startupDelaySeconds = Math.max(environment.getProperty("spring.quartz.startup-delay-seconds", Integer.class, 2), 0);
        factoryBean.setStartupDelay(startupDelaySeconds);
        return factoryBean;
    }

    private static void copyQuartzProperty(Environment environment, Properties target, String quartzKey) {
        String value = environment.getProperty("spring.quartz.properties." + quartzKey);
        if (value != null && !value.isBlank()) {
            target.setProperty(quartzKey, value);
        }
    }
}
