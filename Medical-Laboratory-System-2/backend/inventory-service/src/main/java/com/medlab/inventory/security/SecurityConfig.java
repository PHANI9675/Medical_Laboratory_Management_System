package com.medlab.inventory.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF — not needed for stateless REST APIs
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless session — no HTTP session, every request must carry JWT
                .sessionManagement(s ->
                        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Define which endpoints need authentication
                .authorizeHttpRequests(auth -> auth

                        // Swagger — allow without token (for testing/docs)
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()

                        // Anyone with a valid token can view tests and inventory
                        .requestMatchers(HttpMethod.GET, "/tests/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/inventory/**").authenticated()

                        // Only ADMIN can add or update tests
                        .requestMatchers(HttpMethod.POST, "/tests/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/tests/**").hasAuthority("ADMIN")

                        // Only ADMIN or LAB_TECH can adjust inventory
                        .requestMatchers(HttpMethod.POST, "/inventory/**")
                        .hasAnyAuthority("ADMIN", "LAB_TECH")

                        // Any other request must be authenticated
                        .anyRequest().authenticated()
                )

                // Add our JWT filter before Spring's default login filter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}