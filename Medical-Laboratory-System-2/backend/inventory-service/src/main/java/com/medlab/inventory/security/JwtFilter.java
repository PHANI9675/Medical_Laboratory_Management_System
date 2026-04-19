package com.medlab.inventory.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip auth endpoints, Swagger UI paths, and webjars static assets
        // without this, the JwtFilter sets status 401 on /webjars/** before the
        // resource handler can write the file, causing the browser to see 401 on all
        // Swagger UI JavaScript/CSS assets and the UI to fail to render.
        if (path.startsWith("/auth")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/webjars")) {
            filterChain.doFilter(request, response);
            return;
        }

        System.out.println("Auth Header: " + request.getHeader("Authorization"));
        String authHeader = request.getHeader("Authorization");
        String token = null;
        String username = null;
        String role = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            System.out.println("Token: " + token);

            try {
                username = jwtUtil.extractUsername(token);
                System.out.println("Username: " + username);
                role = jwtUtil.extractRole(token);
            } catch (Exception e) {
                System.out.println("Extracting Username");
                System.out.println(e.getMessage());
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT");
                return;
            }
        }

        if (username != null && jwtUtil.validateToken(token, username)) {

            System.out.println("VALID TOKEN");

            role = jwtUtil.extractRole(token);

            System.out.println("Role: " + role);

            // FIX: store plain role string (e.g. "ADMIN") so that
            // hasAuthority("ADMIN") and @PreAuthorize("hasAuthority('ADMIN')") work correctly.
            // Previously "ROLE_" + role was used, which caused hasAuthority() checks to always return 403.
            List<SimpleGrantedAuthority> authorities = Collections
                    .singletonList(new SimpleGrantedAuthority(role));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            System.out.println("Authentication set for user: " + username);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        filterChain.doFilter(request, response);
    }
}