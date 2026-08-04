package com.planmate.community.config;

import org.springframework.util.AntPathMatcher;

import java.util.List;

public final class SecurityWhitelist {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    public static final List<String> PATHS = List.of(
            "/actuator/health",
            // 인증 없이 스크랩 가능해야 하는 프로메테우스. 실질적 접근 제한은 network 레벨
            // (management.server.port 분리, 내부망 전용 9092)에서 이뤄진다 — 별도 포트를 써도
            // 이 SecurityFilterChain은 그대로 적용되므로 여기서 명시적으로 열어줘야 한다.
            "/actuator/prometheus",
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
