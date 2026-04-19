package com.medlab.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Programmatic route definitions for the API Gateway.
 *
 * Spring Cloud 2025.1.x renamed the gateway artifact from
 * spring-cloud-starter-gateway  →  spring-cloud-starter-gateway-server-webflux
 * and reorganised its configuration properties under the new namespace
 * spring.cloud.gateway.server.webflux.*  (previously spring.cloud.gateway.*).
 *
 * Defining routes here as a RouteLocator bean ensures they are ALWAYS registered
 * by Spring Cloud Gateway regardless of which property namespace version it reads,
 * because Java-bean routes bypass the YAML properties loader entirely.
 *
 * Route order matters for overlapping predicates:
 *   /patient/profile  →  user-service  (explicit, listed BEFORE /patient/**)
 *   /patient/**       →  patient-service
 * The builder registers routes in declaration order; first match wins.
 */
@Configuration
public class RouteLocatorConfig {

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()

            // ── Auth Service (user-service) ────────────────────────────────
            // /patient/profile and /patient/reports are handled by auth-service's
            // own PatientController. Must be declared BEFORE /patient/** below.
            .route("user-service", r -> r
                .path("/auth/**", "/admin/**", "/labTech/**",
                      "/patient/profile", "/patient/reports")
                .uri("lb://user-service"))

            // ── Patient Service ────────────────────────────────────────────
            .route("patient-service", r -> r
                .path("/patient/**")
                .uri("lb://patient-service"))

            // ── Order Service ──────────────────────────────────────────────
            .route("order-service", r -> r
                .path("/orders/**")
                .uri("lb://order-service"))

            // ── Lab Processing Service (LPS) ───────────────────────────────
            .route("lps", r -> r
                .path("/api/jobs/**")
                .uri("lb://lps"))

            // ── Inventory Service ──────────────────────────────────────────
            .route("inventory-service", r -> r
                .path("/tests/**", "/inventory/**")
                .uri("lb://inventory-service"))

            // ── Billing Service ────────────────────────────────────────────
            .route("billing-service", r -> r
                .path("/billing/**", "/invoices/**", "/payments/**")
                .uri("lb://billing-service"))

            // ── Notification Service ───────────────────────────────────────
            .route("notification-service", r -> r
                .path("/notification/**")
                .uri("lb://notification-service"))

            // ── Swagger / API-docs aggregation ─────────────────────────────
            // setPath() is used instead of rewritePath() because rewritePath() treats
            // the first argument as a Java regex and can silently fail to rewrite in
            // Spring Cloud Gateway 2025.1.x, causing the downstream service to receive
            // the original path (e.g. /v3/api-docs/user-service) and return 404 because
            // that path is not a registered SpringDoc group. setPath() directly replaces
            // the request path with no regex involved.
            .route("user-service-docs", r -> r
                .path("/v3/api-docs/user-service")
                .filters(f -> f.setPath("/v3/api-docs"))
                .uri("lb://user-service"))

            .route("patient-service-docs", r -> r
                .path("/v3/api-docs/patient-service")
                .filters(f -> f.setPath("/v3/api-docs"))
                .uri("lb://patient-service"))

            .route("order-service-docs", r -> r
                .path("/v3/api-docs/order-service")
                .filters(f -> f.setPath("/v3/api-docs"))
                .uri("lb://order-service"))

            .route("lps-docs", r -> r
                .path("/v3/api-docs/lps")
                .filters(f -> f.setPath("/v3/api-docs"))
                .uri("lb://lps"))

            .route("inventory-service-docs", r -> r
                .path("/v3/api-docs/inventory-service")
                .filters(f -> f.setPath("/v3/api-docs"))
                .uri("lb://inventory-service"))

            .route("billing-service-docs", r -> r
                .path("/v3/api-docs/billing-service")
                .filters(f -> f.setPath("/v3/api-docs"))
                .uri("lb://billing-service"))

            .route("notification-service-docs", r -> r
                .path("/v3/api-docs/notification-service")
                .filters(f -> f.setPath("/v3/api-docs"))
                .uri("lb://notification-service"))

            .build();
    }
}
