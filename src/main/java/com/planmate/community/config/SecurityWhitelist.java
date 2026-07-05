package com.planmate.community.config;

import org.springframework.util.AntPathMatcher;

import java.util.List;

public final class SecurityWhitelist {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    public static final List<String> PATHS = List.of(
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    );

    private SecurityWhitelist() {
    }

    public static boolean isWhitelisted(String uri) {
        return PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, uri));
    }
}
