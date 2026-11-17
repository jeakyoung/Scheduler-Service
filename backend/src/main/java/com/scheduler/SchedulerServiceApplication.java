package com.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Extends SpringBootServletInitializer (PLAN.md §11.1) so an external Tomcat deploying the
 * packaged WAR can bootstrap the Spring context via web.xml-less Servlet 3+ initialization.
 * main()/SpringApplication.run(...) is kept as-is for local dev (`mvn spring-boot:run`) and
 * running the jar directly - this class works both ways, it isn't an either/or switch.
 */
@SpringBootApplication
public class SchedulerServiceApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerServiceApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(SchedulerServiceApplication.class);
    }
}
