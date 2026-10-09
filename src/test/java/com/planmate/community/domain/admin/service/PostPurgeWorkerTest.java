package com.planmate.community.domain.admin.service;

import com.planmate.community.domain.admin.enums.ModerationTargetType;
import com.planmate.community.domain.admin.repository.ModerationRepository;
import com.planmate.community.domain.image.service.ImageService;
import com.planmate.community.domain.stats.service.UserStatsService;
import com.planmate.community.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Import({ModerationRepository.class, PostPurgeWorker.class})
class PostPurgeWorkerTest extends PostgresTestBase {

    @Autowired PostPurgeWorker worker;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean UserStatsService userStatsService;
    @MockitoBean ImageService imageService;

    final UUID author = UUID.randomUUID();
    final UUID commenter = UUID.randomUUID();
    final UUID liker = UUID.randomUUID();

    @Test void purgingACommunityPostRemovesItsChildrenAndRollsBackEngagementStats() {
        insertCommunityPost(7001L, LocalDateTime.now().minusDays(31), "http://img/thumb.png");
        jdbc.update("INSERT INTO community_comment (post_id, user_id, author_nickname, content, deleted_at, created_at, updated_at)"
                + " VALUES (7001, ?, '댓글러', '살아 있는 댓글', NULL, now(), now())", commenter);
        jdbc.update("INSERT INTO community_comment (post_id, user_id, author_nickname, content, deleted_at, created_at, updated_at)"
                + " VALUES (7001, ?, '댓글러', '이미 지운 댓글', now(), now(), now())", commenter);
        jdbc.update("INSERT INTO community_reaction (post_id, user_id, type, created_at) VALUES (7001, ?, 'LIKE', now())", liker);
        jdbc.update("INSERT INTO community_reaction (post_id, user_id, type, created_at) VALUES (7001, ?, 'LIKE', now())", UUID.randomUUID());
        jdbc.update("INSERT INTO community_reaction (post_id, user_id, type, created_at) VALUES (7001, ?, 'DISLIKE', now())", UUID.randomUUID());
        reportPost(7001L);
        when(imageService.extractImageUrls(anyString())).thenReturn(new HashSet<>(Set.of("http://img/body.png")));

        Set<String> urls = worker.purge(ModerationTargetType.POST, 7001L);

        assertThat(urls).containsExactlyInAnyOrder("http://img/body.png", "http://img/thumb.png");
        assertThat(count("community_post", 7001L)).isZero();
        assertThat(count("community_comment", 7001L)).isZero();
        assertThat(count("community_reaction", 7001L)).isZero();
        assertThat(count("post_report", 7001L)).isZero();
        // 살아 있던 댓글 한 건만 통계에서 빠진다. 이미 지운 댓글과 싫어요는 세지 않는다.
        verify(userStatsService).recordCommentsPurged(commenter, 1);
        verify(userStatsService).recordLikeReceived(author, -2);
    }

    @Test void purgingAFeedPostReliesOnCascadeForItsChildren() {
        insertFeedPost(7002L, LocalDateTime.now().minusDays(31));
        jdbc.update("INSERT INTO feed_comment (post_id, user_id, author_nickname, content, created_at, updated_at)"
                + " VALUES (7002, ?, '댓글러', '여행 좋아요', now(), now())", commenter);
        jdbc.update("INSERT INTO feed_reaction (post_id, user_id, type, created_at) VALUES (7002, ?, 'LIKE', now())", liker);
        reportFeed(7002L);
        when(imageService.extractImageUrls(anyString())).thenReturn(new HashSet<>());

        worker.purge(ModerationTargetType.FEED, 7002L);

        assertThat(count("feed_post", 7002L)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_comment WHERE post_id = 7002", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM feed_reaction WHERE post_id = 7002", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_report WHERE feed_id = 7002", Integer.class)).isZero();
        verify(userStatsService).recordCommentsPurged(commenter, 1);
        verify(userStatsService).recordLikeReceived(author, -1);
    }

    @Test void purgingAMissingPostIsANoOp() {
        assertThat(worker.purge(ModerationTargetType.POST, 999999L)).isEmpty();
        verifyNoInteractions(userStatsService);
    }

    private long count(String table, long postId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE post_id = ?", Long.class, postId);
    }

    private void insertCommunityPost(long postId, LocalDateTime deletedAt, String thumbnailUrl) {
        jdbc.update("""
                INSERT INTO community_post (post_id, category, user_id, author_nickname, title, content, content_text,
                    thumbnail_url, deleted_at, created_at, updated_at)
                VALUES (?, 'FREE', ?, '작성자', '글', '[]'::jsonb, '', ?, ?, now(), now())
                """, postId, author, thumbnailUrl, deletedAt);
    }

    private void insertFeedPost(long postId, LocalDateTime deletedAt) {
        jdbc.update("""
                INSERT INTO feed_post (post_id, category, user_id, author_nickname, title, content, content_text,
                    region, duration_days, deleted_at, created_at, updated_at)
                VALUES (?, 'FEED', ?, '작성자', '여행기', '[]'::jsonb, '', '서울', 1, ?, now(), now())
                """, postId, author, deletedAt);
    }

    private void reportPost(long postId) {
        jdbc.update("INSERT INTO post_report (post_id, reporter_id, reason, details) VALUES (?, ?, 'SPAM', '')",
                postId, UUID.randomUUID());
    }

    private void reportFeed(long feedId) {
        jdbc.update("INSERT INTO post_report (feed_id, reporter_id, reason, details) VALUES (?, ?, 'SPAM', '')",
                feedId, UUID.randomUUID());
    }
}
