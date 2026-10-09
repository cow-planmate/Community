package com.planmate.community.domain.admin.repository;

import com.planmate.community.domain.admin.dto.AdminDeletedPostResponse;
import com.planmate.community.domain.admin.dto.AdminReportTargetResponse;
import com.planmate.community.domain.admin.enums.ModerationActionType;
import com.planmate.community.domain.admin.enums.ModerationTargetType;
import com.planmate.community.domain.report.enums.ReportStatus;
import com.planmate.community.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Import(ModerationRepository.class)
class ModerationRepositoryTest extends PostgresTestBase {

    @Autowired ModerationRepository repository;
    @Autowired JdbcTemplate jdbc;

    final UUID author = UUID.randomUUID();
    final LocalDateTime now = LocalDateTime.now();

    @Test void reportTargetsGroupReportsPerTargetAndFilterByStatus() {
        insertCommunityPost(8001L, null);
        insertCommunityPost(8002L, now.minusDays(2));
        insertFeedPost(8003L, null);
        reportPost(8001L, "SPAM", "PENDING", now.minusHours(3));
        reportPost(8001L, "ABUSE", "PENDING", now.minusHours(1));
        reportPost(8002L, "PRIVACY", "RESOLVED", now.minusHours(2));
        reportFeed(8003L, "OTHER", "PENDING", now.minusHours(4));

        List<AdminReportTargetResponse> pending = repository.findReportTargets(ReportStatus.PENDING, null, 10, 0);

        assertThat(pending).extracting(AdminReportTargetResponse::targetType, AdminReportTargetResponse::targetId)
                .containsExactly(tuple("POST", 8001L), tuple("FEED", 8003L));
        AdminReportTargetResponse post = pending.get(0);
        assertThat(post.statusCount()).isEqualTo(2);
        assertThat(post.totalCount()).isEqualTo(2);
        assertThat(post.reasons()).containsExactlyInAnyOrder("SPAM", "ABUSE");
        assertThat(repository.countReportTargets(ReportStatus.PENDING, ModerationTargetType.POST)).isEqualTo(1);

        List<AdminReportTargetResponse> resolved = repository.findReportTargets(ReportStatus.RESOLVED, null, 10, 0);
        assertThat(resolved).extracting(AdminReportTargetResponse::targetId).containsExactly(8002L);
        assertThat(resolved.get(0).deletedAt()).isNotNull();
    }

    @Test void deletedPostsAreListedOnlyWithinTheRetentionWindowAndCanBeRestored() {
        insertCommunityPost(8101L, now.minusDays(10));
        insertCommunityPost(8102L, now.minusDays(40));
        LocalDateTime cutoff = now.minusDays(30);

        List<AdminDeletedPostResponse> deleted =
                repository.findDeletedPosts(ModerationTargetType.POST, cutoff, 30, 10, 0);

        assertThat(deleted).extracting(AdminDeletedPostResponse::postId).containsExactly(8101L);
        assertThat(deleted.get(0).expiresAt()).isEqualTo(deleted.get(0).deletedAt().plusDays(30));
        assertThat(repository.countDeletedPosts(ModerationTargetType.POST, cutoff)).isEqualTo(1);

        assertThat(repository.restorePost(ModerationTargetType.POST, 8102L, cutoff)).isZero();
        assertThat(repository.restorePost(ModerationTargetType.POST, 8101L, cutoff)).isEqualTo(1);
        assertThat(repository.findPostState(ModerationTargetType.POST, 8101L).orElseThrow().isDeleted()).isFalse();
    }

    @Test void expiredPostIdsContainOnlyPostsPastTheRetentionWindow() {
        insertCommunityPost(8201L, now.minusDays(40));
        insertCommunityPost(8202L, now.minusDays(5));
        insertCommunityPost(8203L, null);

        assertThat(repository.findExpiredPostIds(ModerationTargetType.POST, now.minusDays(30), 10))
                .containsExactly(8201L);
    }

    @Test void markPendingReportsTouchesOnlyPendingRows() {
        insertCommunityPost(8301L, null);
        reportPost(8301L, "SPAM", "PENDING", now);
        reportPost(8301L, "ABUSE", "DISMISSED", now);
        UUID admin = UUID.randomUUID();

        int updated = repository.markPendingReports(ModerationTargetType.POST, 8301L, ReportStatus.RESOLVED, admin, now);

        assertThat(updated).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM post_report WHERE post_id = 8301 AND status = 'DISMISSED'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT handled_by FROM post_report WHERE post_id = 8301 AND status = 'RESOLVED'", UUID.class))
                .isEqualTo(admin);
    }

    @Test void adminActionLogRecordsTheAction() {
        repository.insertAdminAction(UUID.randomUUID(), ModerationActionType.DELETE_CONTENT,
                ModerationTargetType.FEED, 8401L, "스팸", now);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM community_admin_action_log WHERE target_type = 'FEED' AND target_id = 8401",
                Integer.class)).isEqualTo(1);
    }

    private void insertCommunityPost(long postId, LocalDateTime deletedAt) {
        jdbc.update("""
                INSERT INTO community_post (post_id, category, user_id, author_nickname, title, content, content_text,
                    deleted_at, created_at, updated_at)
                VALUES (?, 'FREE', ?, '작성자', '글', '[]'::jsonb, '', ?, now(), now())
                """, postId, author, deletedAt);
    }

    private void insertFeedPost(long postId, LocalDateTime deletedAt) {
        jdbc.update("""
                INSERT INTO feed_post (post_id, category, user_id, author_nickname, title, content, content_text,
                    region, duration_days, deleted_at, created_at, updated_at)
                VALUES (?, 'FEED', ?, '작성자', '여행기', '[]'::jsonb, '', '서울', 1, ?, now(), now())
                """, postId, author, deletedAt);
    }

    private void reportPost(long postId, String reason, String status, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO post_report (post_id, reporter_id, reason, details, status, created_at)"
                + " VALUES (?, ?, ?, '', ?, ?)", postId, UUID.randomUUID(), reason, status, createdAt);
    }

    private void reportFeed(long feedId, String reason, String status, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO post_report (feed_id, reporter_id, reason, details, status, created_at)"
                + " VALUES (?, ?, ?, '', ?, ?)", feedId, UUID.randomUUID(), reason, status, createdAt);
    }
}
