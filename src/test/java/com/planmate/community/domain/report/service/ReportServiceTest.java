package com.planmate.community.domain.report.service;

import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.post.entity.CommunityPost;
import com.planmate.community.domain.post.entity.FeedPost;
import com.planmate.community.domain.post.repository.PostRepository;
import com.planmate.community.domain.post.repository.FeedPostRepository;
import com.planmate.community.domain.report.dto.ReportRequest;
import com.planmate.community.domain.report.enums.ReportReason;
import com.planmate.community.domain.report.repository.ReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    @Mock PostRepository posts;
    @Mock FeedPostRepository feeds;
    @Mock ReportRepository reports;
    @InjectMocks ReportService service;
    final UUID reporter = UUID.randomUUID();
    final UUID author = UUID.randomUUID();
    final ReportRequest request = new ReportRequest(ReportReason.SPAM, " 광고 ");

    @Test void communityReportIsSavedWithTrimmedDetails() {
        CommunityPost post = mock(CommunityPost.class);
        when(post.getUserId()).thenReturn(author);
        when(posts.findById(1L)).thenReturn(Optional.of(post));
        when(reports.insert(1L, reporter, "SPAM", "광고", false)).thenReturn(true);
        service.report(reporter, 1L, request, false);
        verify(reports).insert(1L, reporter, "SPAM", "광고", false);
        verifyNoInteractions(feeds);
    }

    @Test void feedUsesSeparateDomainEvenWithSameId() {
        FeedPost post = mock(FeedPost.class);
        when(post.getUserId()).thenReturn(author);
        when(feeds.findById(1L)).thenReturn(Optional.of(post));
        when(reports.insert(1L, reporter, "SPAM", "광고", true)).thenReturn(true);
        service.report(reporter, 1L, request, true);
        verifyNoInteractions(posts);
    }

    @Test void duplicateReportReturnsConflict() {
        CommunityPost post = mock(CommunityPost.class);
        when(post.getUserId()).thenReturn(author);
        when(posts.findById(1L)).thenReturn(Optional.of(post));
        assertError(() -> service.report(reporter, 1L, request, false), ErrorCode.REPORT_ALREADY_SUBMITTED);
    }

    @Test void ownPostCannotBeReported() {
        FeedPost post = mock(FeedPost.class);
        when(post.getUserId()).thenReturn(reporter);
        when(feeds.findById(1L)).thenReturn(Optional.of(post));
        assertError(() -> service.report(reporter, 1L, request, true), ErrorCode.REPORT_SELF_DENIED);
        verifyNoInteractions(reports);
    }

    @Test void missingPostCannotBeReported() {
        assertError(() -> service.report(reporter, 1L, request, false), ErrorCode.POST_NOT_FOUND);
        assertError(() -> service.report(reporter, 1L, request, true), ErrorCode.POST_NOT_FOUND);
        verifyNoInteractions(reports);
    }

    @Test void invalidDetailsCannotBeReported() {
        assertError(() -> service.report(reporter, 1L, new ReportRequest(ReportReason.OTHER, " "), false), ErrorCode.INVALID_INPUT);
        assertError(() -> service.report(reporter, 1L, new ReportRequest(ReportReason.SPAM, "a".repeat(1001)), true), ErrorCode.INVALID_INPUT);
        assertError(() -> service.report(reporter, 1L, new ReportRequest(null, ""), true), ErrorCode.INVALID_INPUT);
        verifyNoInteractions(posts, feeds, reports);
    }

    @Test void anonymousCannotReport() {
        assertError(() -> service.report(null, 1L, request, false), ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(posts, feeds, reports);
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CommunityException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.getErrorCode()).isEqualTo(code));
    }
}
