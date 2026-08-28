package com.planmate.community.domain.post.repository;

import com.planmate.community.domain.comment.entity.FeedComment;
import com.planmate.community.domain.comment.repository.FeedCommentRepository;
import com.planmate.community.domain.post.entity.CommunityPost;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.entity.FeedPost;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.reaction.entity.FeedReaction;
import com.planmate.community.domain.reaction.enums.ReactionType;
import com.planmate.community.domain.reaction.repository.FeedReactionRepository;
import com.planmate.community.support.PostgresTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FeedPostPersistenceTest extends PostgresTestBase {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private FeedPostRepository feedPostRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FeedCommentRepository commentRepository;

    @Autowired
    private FeedReactionRepository reactionRepository;

    @Test
    @DisplayName("피드는 community_post 없이 feed_post에 독립 저장된다")
    void storesFeedInDedicatedTable() {
        FeedPost post = feedPost("서울", 3, "[\"도시\",\"맛집\"]");

        FeedPost saved = feedPostRepository.saveAndFlush(post);
        entityManager.clear();

        Integer commonRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM community_post WHERE post_id = ?", Integer.class, saved.getPostId());
        Integer feedRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM feed_post WHERE post_id = ?", Integer.class, saved.getPostId());
        FeedPost loaded = feedPostRepository.findById(saved.getPostId()).orElseThrow();

        assertThat(commonRows).isZero();
        assertThat(feedRows).isEqualTo(1);
        assertThat(loaded.getRegion()).isEqualTo("서울");
        assertThat(loaded.getDurationDays()).isEqualTo(3);
        assertThat(loaded.getTags()).contains("도시", "맛집");
    }

    @Test
    @DisplayName("피드 필터와 포크 카운터 갱신이 community_feed를 사용한다")
    void queriesAndUpdatesDedicatedFeedTable() {
        FeedPost saved = feedPostRepository.saveAndFlush(feedPost("제주", 4, "[\"바다\"]"));
        entityManager.clear();

        var page = feedPostRepository.findFeedPosts("제주", 3, 5, "바다", null, PageRequest.of(0, 10));
        assertThat(page.getContent()).extracting(Post::getPostId).containsExactly(saved.getPostId());

        feedPostRepository.addForkCount(saved.getPostId(), 1);
        entityManager.clear();
        assertThat(feedPostRepository.findById(saved.getPostId()).orElseThrow().getForkCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("일반 커뮤니티 글은 feed_post 행을 만들지 않는다")
    void communityPostDoesNotCreateFeedRow() {
        CommunityPost post = CommunityPost.builder()
                .category(Category.FREE)
                .userId(UUID.randomUUID())
                .authorNickname("작성자")
                .title("자유 글")
                .content("{}")
                .contentText("본문")
                .build();

        Post saved = postRepository.saveAndFlush(post);

        Integer feedRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM feed_post WHERE post_id = ?", Integer.class, saved.getPostId());
        assertThat(feedRows).isZero();
    }

    @Test
    @DisplayName("피드 댓글과 반응도 커뮤니티 테이블과 분리된다")
    void storesFeedInteractionsInDedicatedTables() {
        FeedPost saved = feedPostRepository.saveAndFlush(feedPost("부산", 2, null));
        UUID userId = UUID.randomUUID();

        commentRepository.saveAndFlush(FeedComment.create(saved.getPostId(), userId, "댓글작성자", "좋아요", null));
        reactionRepository.saveAndFlush(FeedReaction.create(saved.getPostId(), userId, ReactionType.LIKE));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM feed_comment WHERE post_id = ?", Integer.class, saved.getPostId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM community_comment WHERE post_id = ?", Integer.class, saved.getPostId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM feed_reaction WHERE post_id = ?", Integer.class, saved.getPostId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM community_reaction WHERE post_id = ?", Integer.class, saved.getPostId())).isZero();
    }

    private FeedPost feedPost(String region, int durationDays, String tags) {
        return FeedPost.create(UUID.randomUUID(), "작성자", region + " 여행", "{}", "본문", null,
                region, region, 37.5665, 126.9780, durationDays, null, tags, null);
    }
}
