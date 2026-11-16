package com.scheduler.engine.quartz;

import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
import org.quartz.spi.TriggerFiredBundle;

import java.util.Properties;

/**
 * JDBC JobStore table layout (QRTZ_*) is applied by Flyway (T3). The two things Spring
 * Boot's Quartz autoconfiguration does NOT give us for free are:
 *  1. Dependency injection into Job instances - Quartz creates Job objects itself via
 *     reflection, bypassing the Spring container, so GenericScheduleJob's @Autowired
 *     ScheduleExecutor would stay null without this.
 *  2. A driverDelegateClass that works for whichever DB is actually active. SQL Server
 *     needs MSSQLDelegate (StdJDBCDelegate's "SELECT ... FOR UPDATE" isn't valid T-SQL -
 *     see LOG.md's T3 entry), while H2 (the local-dev DB_URL fallback) needs the plain
 *     StdJDBCDelegate instead. All org.quartz.* properties are therefore set here in code,
 *     not in application.yml, so this branch has one place to live - spring.quartz.properties
 *     is intentionally left empty in application.yml.
 */
@Configuration
public class QuartzConfig {

    @Bean
    public SchedulerFactoryBeanCustomizer autowiringJobFactoryCustomizer(ApplicationContext applicationContext) {
        return (SchedulerFactoryBean factory) -> factory.setJobFactory(new AutowiringSpringBeanJobFactory(applicationContext));
    }

    @Bean
    public SchedulerFactoryBeanCustomizer jobStorePropertiesCustomizer(@Value("${spring.datasource.url}") String jdbcUrl) {
        return (SchedulerFactoryBean factory) -> {
            Properties quartzProperties = new Properties();
            quartzProperties.setProperty("org.quartz.jobStore.class", "org.springframework.scheduling.quartz.LocalDataSourceJobStore");
            quartzProperties.setProperty("org.quartz.jobStore.driverDelegateClass", resolveDriverDelegateClass(jdbcUrl));
            quartzProperties.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
            quartzProperties.setProperty("org.quartz.jobStore.isClustered", "false");
            quartzProperties.setProperty("org.quartz.scheduler.instanceName", "SchedulerServiceQuartz");
            factory.setQuartzProperties(quartzProperties);
        };
    }

    private static String resolveDriverDelegateClass(String jdbcUrl) {
        if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:h2:")) {
            return "org.quartz.impl.jdbcjobstore.StdJDBCDelegate";
        }
        return "org.quartz.impl.jdbcjobstore.MSSQLDelegate";
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
