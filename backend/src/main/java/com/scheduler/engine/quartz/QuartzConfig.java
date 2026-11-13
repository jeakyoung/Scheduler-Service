package com.scheduler.engine.quartz;

import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
import org.quartz.spi.TriggerFiredBundle;

/**
 * JDBC JobStore itself is configured via application.yml's spring.quartz.* properties
 * (backed by T3's QRTZ_* tables, applied by Flyway). The one thing Spring Boot's Quartz
 * autoconfiguration does NOT give us for free is dependency injection into Job instances -
 * Quartz creates Job objects itself via reflection, bypassing the Spring container, so
 * GenericScheduleJob's @Autowired ScheduleExecutor would stay null without this.
 */
@Configuration
public class QuartzConfig {

    @Bean
    public SchedulerFactoryBeanCustomizer autowiringJobFactoryCustomizer(ApplicationContext applicationContext) {
        return (SchedulerFactoryBean factory) -> factory.setJobFactory(new AutowiringSpringBeanJobFactory(applicationContext));
    }

    private static class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory implements ApplicationContextAware {

        private AutowireCapableBeanFactory beanFactory;

        AutowiringSpringBeanJobFactory(ApplicationContext applicationContext) {
            setApplicationContext(applicationContext);
        }

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) {
            this.beanFactory = applicationContext.getAutowireCapableBeanFactory();
        }

        @Override
        protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
            Object job = super.createJobInstance(bundle);
            beanFactory.autowireBean(job);
            return job;
        }
    }
}
