package com.planmate.community.domain.admin.controller;

import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.config.CorsConfig;
import com.planmate.community.config.SecurityConfig;
import com.planmate.community.domain.admin.enums.ModerationTargetType;
import com.planmate.community.domain.admin.service.ModerationService;
import com.planmate.community.domain.report.enums.ReportStatus;
import com.planmate.community.security.GatewayAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/community/admin/**} 는 게이트웨이가 재주입한 ROLE_ADMIN 만 통과해야 한다.
 * 권한이 빠지면 일반 사용자가 남의 글을 지울 수 있으므로 경로 규칙을 직접 검증한다.
 */
@WebMvcTest(AdminModerationController.class)
@Import({SecurityConfig.class, GatewayAuthenticationFilter.class, CorsConfig.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost:5173")
class AdminModerationControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ModerationService moderationService;

    final String adminId = UUID.randomUUID().toString();
    final String userId = UUID.randomUUID().toString();

    @Test void anonymousRequestIsUnauthorized() throws Exception {
        mvc.perform(get("/api/community/admin/reports"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(moderationService);
    }

    @Test void ordinaryUserIsForbiddenEvenWithAValidIdentity() throws Exception {
        mvc.perform(get("/api/community/admin/reports")
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/community/admin/reports/POST/7/delete")
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(moderationService);
    }

    @Test void adminCanListReportsAndDismissWithAReason() throws Exception {
        when(moderationService.reportTargets(eq(ReportStatus.PENDING), isNull(), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mvc.perform(get("/api/community/admin/reports")
                        .header("X-User-Id", adminId)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/community/admin/reports/POST/7/dismiss")
                        .header("X-User-Id", adminId)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"괜찮음\"}"))
                .andExpect(status().isNoContent());

        verify(moderationService).dismiss(UUID.fromString(adminId), ModerationTargetType.POST, 7L, "괜찮음");
    }

    @Test void adminCanDeleteWithoutABody() throws Exception {
        mvc.perform(post("/api/community/admin/reports/FEED/9/delete")
                        .header("X-User-Id", adminId)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent());

        verify(moderationService).deleteContent(eq(UUID.fromString(adminId)), eq(ModerationTargetType.FEED), eq(9L), isNull());
    }

    @Test void overlongReasonIsRejectedBeforeReachingTheService() throws Exception {
        mvc.perform(post("/api/community/admin/reports/POST/7/dismiss")
                        .header("X-User-Id", adminId)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + "가".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest());

        verify(moderationService, never()).dismiss(any(), any(), anyLong(), any());
    }
}
