package com.medlab.gateway.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.ArrayList;
import java.util.List;

/**
 * NOTE: This class is intentionally NOT annotated with @Configuration.
 *
 * Previously it created GroupedOpenApi beans (one per route) that caused
 * SpringDoc's RouterFunctionHandlerMapping (order -1) to register handlers at
 * /v3/api-docs/{service-name}. Because that handler mapping has higher priority
 * than Spring Cloud Gateway's RoutePredicateHandlerMapping (order 1), SpringDoc
 * intercepted GET /v3/api-docs/user-service before the gateway route could
 * forward it downstream, found no meaningful local group content, and returned 404.
 *
 * The springdoc.swagger-ui.urls list in application.yml already configures the
 * Swagger UI dropdown to fetch each service's docs via the gateway routes defined
 * in RouteLocatorConfig.java. No GroupedOpenApi beans are needed.
 */
public class SwaggerConfig {

    // Disabled — see class-level Javadoc above.
    public List<GroupedOpenApi> apis(RouteDefinitionLocator locator) {
        List<GroupedOpenApi> groups = new ArrayList<>();
        locator.getRouteDefinitions()
                .filter(def -> def.getId() != null && !def.getId().startsWith("ReactiveComposite"))
                .subscribe(def -> groups.add(
                        GroupedOpenApi.builder()
                                .group(def.getId())
                                .pathsToMatch("/" + def.getId().replace("-service", "") + "/**",
                                        "/api/**", "/orders/**", "/billing/**",
                                        "/invoices/**", "/payments/**", "/tests/**",
                                        "/inventory/**", "/patient/**", "/auth/**",
                                        "/notification/**")
                                .build()
                ));
        return groups;
    }
}