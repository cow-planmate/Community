package com.planmate.community.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.common.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 정지된 회원은 로그인과 조회는 그대로 두고, 커뮤니티·피드에 글과 댓글을 쓰는 요청만 막는다.
 * X-User-Suspended 는 게이트웨이가 검증된 토큰의 클레임에서만 붙이므로 클라이언트가 위조할 수 없다.
 */
public class SuspendedWriteGuardFilter extends OncePerRequestFilter {

    static final String SUSPENDED_HEADER = "X-User-Suspended";

    private static final List<String> WRITE_PATHS = List.of(
            "/api/community/posts",
            "/api/community/posts/*/comments",
            "/api/feed/posts",
            "/api/feed/posts/*/comments");

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper;

    public SuspendedWriteGuardFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isSuspendedWrite(request)) {
            writeErrorResponse(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isSuspendedWrite(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && "true".equals(request.getHeader(SUSPENDED_HEADER))
                && hasGatewayIdentity()
                && WRITE_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, request.getRequestURI()));
    }

    // GatewayAuthenticationFilter 가 검증된 신원으로만 UUID principal 을 세운다.
    private static boolean hasGatewayIdentity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof UUID;
    }

    private void writeErrorResponse(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.ACCOUNT_SUSPENDED.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(ErrorCode.ACCOUNT_SUSPENDED)));
    }
}
