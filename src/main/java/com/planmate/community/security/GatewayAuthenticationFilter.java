package com.planmate.community.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Gateway가 검증해 재주입한 내부 신원 헤더로 SecurityContext를 구성한다. */
@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "ADMIN");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String userId = singleHeader(request, "X-User-Id");
        String role = singleHeader(request, "X-User-Role");
        if (request.getHeader("X-User-Id") != null || request.getHeader("X-User-Role") != null) {
            SecurityContextHolder.clearContext();
        }
        if (userId != null && role != null && ALLOWED_ROLES.contains(role)) {
            try {
                UUID principal = UUID.fromString(userId);
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
            } catch (IllegalArgumentException ignored) {
                // 보호 경로의 최종 401 처리는 SecurityFilterChain이 담당한다.
            }
        }
        chain.doFilter(request, response);
    }

    private static String singleHeader(HttpServletRequest request, String name) {
        List<String> values = Collections.list(request.getHeaders(name));
        return values.size() == 1 && !values.get(0).isBlank() ? values.get(0) : null;
    }
}
