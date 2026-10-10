package com.planmate.community.domain.admin.service;

import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.admin.enums.ModerationActionType;
import com.planmate.community.domain.admin.enums.ModerationTargetType;
import com.planmate.community.domain.admin.repository.ModerationRepository;
import com.planmate.community.domain.admin.repository.ModerationRepository.PostState;
import com.planmate.community.domain.post.service.PostService;
import com.planmate.community.domain.report.enums.ReportStatus;
import com.planmate.community.domain.stats.service.UserStatsService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock ModerationRepository repository;
    @Mock PostService postService;
    @Mock UserStatsService userStatsService;

    final UUID admin = UUID.randomUUID();
    final UUID author = UUID.randomUUID();
    ModerationService service;

    @BeforeEach
    void setUp() {
        service = new ModerationService(repository, postService, userStatsService, new PostRetentionPolicy(30));
    }

    @Test void dismissMarksPendingReportsAndLogsTheAction() {
        when(repository.hasReports(ModerationTargetType.POST, 7L)).thenReturn(true);
        when(repository.markPendingReports(eq(ModerationTargetType.POST), eq(7L), eq(ReportStatus.DISMISSED), eq(admin), any()))
                .thenReturn(2);

        service.dismiss(admin, ModerationTargetType.POST, 7L, "문제 없음");

        verify(repository).insertAdminAction(eq(admin), eq(ModerationActionType.DISMISS_REPORTS),
                eq(ModerationTargetType.POST), eq(7L), eq("문제 없음"), any());
    }

    @Test void dismissOfUnknownTargetIsRejectedBeforeAnyWrite() {
        when(repository.hasReports(ModerationTargetType.POST, 7L)).thenReturn(false);

        assertError(() -> service.dismiss(admin, ModerationTargetType.POST, 7L, null), ErrorCode.REPORT_TARGET_NOT_FOUND);
        verify(repository, never()).markPendingReports(any(), anyLong(), any(), any(), any());
    }

    @Test void deletingALivePostGoesThroughPostServiceAndResolvesItsReports() {
        when(repository.findPostState(ModerationTargetType.POST, 7L)).thenReturn(Optional.of(new PostState(author, null)));

        service.deleteContent(admin, ModerationTargetType.POST, 7L, "스팸");

        verify(postService).deletePost(admin, true, 7L);
        verify(repository).markPendingReports(eq(ModerationTargetType.POST), eq(7L), eq(ReportStatus.RESOLVED), eq(admin), any());
        verify(repository).insertAdminAction(eq(admin), eq(ModerationActionType.DELETE_CONTENT),
                eq(ModerationTargetType.POST), eq(7L), eq("스팸"), any());
    }

    @Test void deletingAnAlreadyDeletedFeedOnlyResolvesReports() {
        when(repository.findPostState(ModerationTargetType.FEED, 9L))
                .thenReturn(Optional.of(new PostState(author, LocalDateTime.now().minusDays(1))));

        service.deleteContent(admin, ModerationTargetType.FEED, 9L, null);

        verifyNoInteractions(postService);
        verify(repository).markPendingReports(eq(ModerationTargetType.FEED), eq(9L), eq(ReportStatus.RESOLVED), eq(admin), any());
    }

    @Test void deletingALiveFeedUsesTheFeedDeletePath() {
        when(repository.findPostState(ModerationTargetType.FEED, 9L)).thenReturn(Optional.of(new PostState(author, null)));

        service.deleteContent(admin, ModerationTargetType.FEED, 9L, null);

        verify(postService).deleteFeedPost(admin, true, 9L);
        verify(postService, never()).deletePost(any(), anyBoolean(), anyLong());
    }

    @Test void deletingAMissingPostIsNotFound() {
        when(repository.findPostState(ModerationTargetType.POST, 7L)).thenReturn(Optional.empty());

        assertError(() -> service.deleteContent(admin, ModerationTargetType.POST, 7L, null), ErrorCode.POST_NOT_FOUND);
        verifyNoInteractions(postService);
    }

    @Test void restoreWithinTheWindowRestoresTheAuthorsStatsAndLogs() {
        when(repository.findPostState(ModerationTargetType.POST, 7L))
                .thenReturn(Optional.of(new PostState(author, LocalDateTime.now().minusDays(3))));
        when(repository.restorePost(eq(ModerationTargetType.POST), eq(7L), any())).thenReturn(1);

        service.restore(admin, ModerationTargetType.POST, 7L);

        verify(userStatsService).recordPostRestored(author);
        verify(repository).insertAdminAction(eq(admin), eq(ModerationActionType.RESTORE_CONTENT),
                eq(ModerationTargetType.POST), eq(7L), isNull(), any());
    }

    @Test void restoreAfterTheRetentionWindowIsRejected() {
        when(repository.findPostState(ModerationTargetType.POST, 7L))
                .thenReturn(Optional.of(new PostState(author, LocalDateTime.now().minusDays(31))));

        assertError(() -> service.restore(admin, ModerationTargetType.POST, 7L), ErrorCode.POST_RESTORE_NOT_ALLOWED);
        verify(repository, never()).restorePost(any(), anyLong(), any());
        verifyNoInteractions(userStatsService);
    }

    @Test void restoreOfALivePostIsRejected() {
        when(repository.findPostState(ModerationTargetType.POST, 7L)).thenReturn(Optional.of(new PostState(author, null)));

        assertError(() -> service.restore(admin, ModerationTargetType.POST, 7L), ErrorCode.POST_RESTORE_NOT_ALLOWED);
        verify(repository, never()).restorePost(any(), anyLong(), any());
    }

    @Test void restoreThatLosesARaceWithPurgeIsNotFound() {
        when(repository.findPostState(ModerationTargetType.POST, 7L))
                .thenReturn(Optional.of(new PostState(author, LocalDateTime.now().minusDays(3))));
        when(repository.restorePost(eq(ModerationTargetType.POST), eq(7L), any())).thenReturn(0);

        assertError(() -> service.restore(admin, ModerationTargetType.POST, 7L), ErrorCode.POST_NOT_FOUND);
        verifyNoInteractions(userStatsService);
    }

    @Test void emptyReportListSkipsTheRowQuery() {
        when(repository.countReportTargets(ReportStatus.PENDING, null)).thenReturn(0L);

        PageResponse<?> page = service.reportTargets(ReportStatus.PENDING, null, 0, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.totalPages()).isZero();
        verify(repository, never()).findReportTargets(any(), any(), anyInt(), anyLong());
    }

    private static void assertError(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CommunityException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
