package com.scheduler.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SPA fallback routing (PLAN.md §11.3): the React Router app has client-side routes (e.g.
 * /schedules) that don't correspond to any static file in the WAR. Falling back to
 * index.html for any unresolved path under /** lets the client-side router take over.
 *
 * /api/** is NOT excluded here on purpose - Spring MVC dispatches to the most specific
 * HandlerMapping, and RequestMappingHandlerMapping (backing @RestController methods) is
 * checked before the SimpleUrlHandlerMapping this resource handler registers, so /api/**
 * requests never reach getResource() below. SpaFallbackIntegrationTest's unauthenticated
 * GET /api/schedules -> 401 (not 200 index.html) assertion is the real-world check of that
 * assumption, not just a claim.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        return requested.exists() && requested.isReadable()
                                ? requested
                                : new ClassPathResource("/static/index.html");
                    }
                });
    }
}
