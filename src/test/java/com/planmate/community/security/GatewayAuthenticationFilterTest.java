package com.planmate.community.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAuthenticationFilterTest {
    private final GatewayAuthenticationFilter filter = new GatewayAuthenticationFilter();

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void createsUuidPrincipalAndAdminAuthority() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = request(userId.toString(), "ADMIN");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userId);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").containsExactly("ROLE_ADMIN");
    }

    @Test void malformedIdentityStaysAnonymous() throws Exception {
        filter.doFilter(request("not-a-uuid", "USER"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static MockHttpServletRequest request(String userId, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", userId);
        request.addHeader("X-User-Role", role);
        return request;
    }
}
