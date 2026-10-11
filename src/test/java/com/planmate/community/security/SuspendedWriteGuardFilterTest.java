package com.planmate.community.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SuspendedWriteGuardFilterTest {

    private final SuspendedWriteGuardFilter filter = new SuspendedWriteGuardFilter(new ObjectMapper());

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void suspendedMemberCannotWriteCommunityPostsOrComments() throws Exception {
        authenticate();

        assertBlocked("POST", "/api/community/posts");
        assertBlocked("POST", "/api/community/posts/12/comments");
    }

    @Test
    void suspendedMemberCannotWriteFeedPostsOrComments() throws Exception {
        authenticate();

        assertBlocked("POST", "/api/feed/posts");
        assertBlocked("POST", "/api/feed/posts/7/comments");
    }

    @Test
    void suspendedMemberKeepsReadingAndOtherActions() throws Exception {
        authenticate();

        assertPassed("GET", "/api/community/posts/12/comments", true);
        assertPassed("GET", "/api/feed/posts", true);
        assertPassed("PATCH", "/api/community/posts/12", true);
        assertPassed("PUT", "/api/feed/posts/7/reaction", true);
        assertPassed("POST", "/api/feed/posts/7/fork", true);
    }

    @Test
    void activeMemberIsNotBlocked() throws Exception {
        authenticate();

        assertPassed("POST", "/api/community/posts", false);
        assertPassed("POST", "/api/feed/posts/7/comments", false);
    }

    @Test
    void suspendedHeaderWithoutGatewayIdentityIsLeftToSecurityChain() throws Exception {
        // 신원이 세워지지 않은 요청은 이 필터가 막지 않고, 401 판단은 뒤의 보안 체인에 맡긴다.
        assertPassed("POST", "/api/community/posts", true);
    }

    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                UUID.randomUUID(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private void assertBlocked(String method, String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request(method, path, true), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("USER_003");
        assertThat(chain.getRequest()).isNull();
    }

    private void assertPassed(String method, String path, boolean suspended) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request(method, path, suspended), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletRequest request(String method, String path, boolean suspended) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (suspended) {
            request.addHeader("X-User-Suspended", "true");
        }
        return request;
    }
}
