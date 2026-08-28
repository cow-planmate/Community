package com.planmate.community.domain.post.repository;

import com.planmate.community.domain.comment.repository.CommentRepository;
import com.planmate.community.domain.post.entity.CommunityPost;
import com.planmate.community.domain.reaction.repository.ReactionRepository;
import com.planmate.community.support.PostgresTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 피드와 커뮤니티는 ID 시퀀스가 분리돼 있어 같은 번호가 양쪽에 존재할 수 있다.
 * 그 상태에서 커뮤니티 조회·카운터가 피드 행을 건드리지 않는지 확인한다.
 */
class DomainIdCollisionTest extends PostgresTestBase {

    private static final long SHARED_ID = 90001L;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private FeedPostRepository feedPostRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ReactionRepository reactionRepository;

    @BeforeEach
    void insertCollidingRows() {
        jdbcTemplate.update("""
                INSERT INTO community_post
                    (post_id, category, user_id, author_nickname, title, content, content_text,
                     like_count, dislike_count, comment_count, view_count, created_at, updated_at)
                VALUES (?, 'FREE', ?, '커뮤니티작성자', '커뮤니티 글', '{}'::jsonb, '본문',
                        0, 0, 0, 0, now(), now())
                """, SHARED_ID, UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO feed_post
                    (post_id, category, user_id, author_nickname, title, content, content_text,
                     like_count, dislike_count, comment_count, view_count, region, duration_days,
                     fork_count, created_at, updated_at)
                VALUES (?, 'FEED', ?, '피드작성자', '피드 글', '{}'::jsonb, '본문',
                        0, 0, 0, 0, '서울', 2, 0, now(), now())
                """, SHARED_ID, UUID.randomUUID());
        entityManager.clear();
    }

    @Test
    @DisplayName("번호가 겹쳐도 커뮤니티 조회는 커뮤니티 글만 본다")
    void communityLookupDoesNotSeeFeedRow() {
        Optional<CommunityPost> found = postRepository.findById(SHARED_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("커뮤니티 글");
        assertThat(found.get()).isExactlyInstanceOf(CommunityPost.class);
    }

    @Test
    @DisplayName("피드에만 있는 번호는 커뮤니티 조회에 잡히지 않는다")
    void communityLookupIgnoresFeedOnlyId() {
        long feedOnlyId = SHARED_ID + 1;
        jdbcTemplate.update("""
                INSERT INTO feed_post
                    (post_id, category, user_id, author_nickname, title, content, content_text,
                     like_count, dislike_count, comment_count, view_count, region, duration_days,
                     fork_count, created_at, updated_at)
                VALUES (?, 'FEED', ?, '피드작성자', '피드 전용 글', '{}'::jsonb, '본문',
                        0, 0, 0, 0, '부산', 3, 0, now(), now())
                """, feedOnlyId, UUID.randomUUID());
        entityManager.clear();

        assertThat(postRepository.findById(feedOnlyId)).isEmpty();
        assertThat(postRepository.existsById(feedOnlyId)).isFalse();
    }

    @Test
    @DisplayName("번호가 겹쳐도 커뮤니티 카운터는 피드 카운터를 건드리지 않는다")
    void communityCounterDoesNotTouchFeedRow() {
        postRepository.addLikeCount(SHARED_ID, 1);
        postRepository.addCommentCount(SHARED_ID, 1);
        postRepository.addViewCount(SHARED_ID, 1);
        entityManager.clear();

        assertThat(feedPostRepository.findById(SHARED_ID)).isPresent().get()
                .satisfies(feed -> {
                    assertThat(feed.getLikeCount()).isZero();
                    assertThat(feed.getCommentCount()).isZero();
                    assertThat(feed.getViewCount()).isZero();
                });
    }

    @Test
    @DisplayName("번호가 겹쳐도 커뮤니티 댓글 목록에 피드 댓글이 섞이지 않는다")
    void communityCommentsExcludeFeedComments() {
        jdbcTemplate.update("""
                INSERT INTO community_comment
                    (comment_id, post_id, user_id, author_nickname, content, created_at, updated_at)
                VALUES (?, ?, ?, '커뮤니티작성자', '커뮤니티 댓글', now(), now())
                """, 7001L, SHARED_ID, UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO feed_comment
                    (comment_id, post_id, user_id, author_nickname, content, created_at, updated_at)
                VALUES (?, ?, ?, '피드작성자', '피드 댓글', now(), now())
                """, 7001L, SHARED_ID, UUID.randomUUID());
        entityManager.clear();

        assertThat(commentRepository.findByPostIdOrderByCreatedAtAsc(SHARED_ID, PageRequest.of(0, 10)))
                .extracting(c -> c.getContent())
                .containsExactly("커뮤니티 댓글");
    }

    @Test
    @DisplayName("번호가 겹쳐도 커뮤니티 반응 조회가 피드 반응을 집어오지 않는다")
    void communityReactionExcludesFeedReaction() {
        UUID reader = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO feed_reaction (reaction_id, post_id, user_id, type, created_at)
                VALUES (?, ?, ?, 'LIKE', now())
                """, 7002L, SHARED_ID, reader);
        entityManager.clear();

        assertThat(reactionRepository.findByPostIdAndUserId(SHARED_ID, reader)).isEmpty();
    }
}
