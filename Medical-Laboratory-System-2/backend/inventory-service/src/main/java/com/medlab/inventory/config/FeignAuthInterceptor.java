package com.medlab.inventory.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Forwards the incoming JWT Authorization header to all outbound Feign calls.
 *
 * When stock is adjusted (POST /inventory/adjust), the caller (ADMIN or LAB_TECH)
 * carries a valid JWT. This interceptor propagates it to any downstream Feign call
 * so the receiving service can validate the token if required.
 *
 * Note: POST /notification in Notification_service is open (permitAll), so the
 * token is not strictly needed for that call — but forwarding it is harmless and
 * consistent with how every other service in this project is set up.
 */
@Configuration
public class FeignAuthInterceptor {

    @Bean
    public RequestInterceptor jwtTokenRelayInterceptor() {
        return requestTemplate -> {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String authHeader = attrs.getRequest().getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    requestTemplate.header("Authorization", authHeader);
                }
            }
        };
    }
}
