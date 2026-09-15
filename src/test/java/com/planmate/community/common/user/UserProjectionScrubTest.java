package com.planmate.community.common.user;

import build.buf.gen.planmate.internal.v1.InternalUser;
import build.buf.gen.planmate.internal.v1.WatchUserChangesResponse;
import com.planmate.community.common.client.AuthorProfile;
import com.planmate.community.domain.comment.entity.CommunityComment;
import com.planmate.community.domain.comment.entity.FeedComment;
import com.planmate.community.domain.comment.repository.CommentRepository;
import com.planmate.community.domain.comment.repository.FeedCommentRepository;
import com.planmate.community.domain.post.entity.CommunityPost;
import com.planmate.community.domain.post.entity.FeedPost;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.post.repository.FeedPostRepository;
import com.planmate.community.domain.post.repository.PostRepository;
import com.planmate.community.support.PostgresTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 탈퇴 이벤트 반영 시 게시글/댓글의 작성자 닉네임 스냅샷을 스크럽하는지 검증.
 *
 * <p>{@link UserProjectionService}는 {@code @DataJpaTest} 슬라이스가 기본으로 스캔하지 않는
 * {@code @Service} 라서, 슬라이스가 자동으로 올려주는 리포지토리 빈들로 직접 생성해 쓴다
 * (UserProjectionPersistenceTest 와 같은 이유로 이 모듈에서 SQL을 실제로 실행하는 테스트다 —
 * @Modifying 벌크 UPDATE의 JPQL 프로퍼티명(authorNickname/userId)이 실제 컬럼에 맞는지는
 * 목으로 확인되지 않는다).
 */
class UserProjectionScrubTest extends PostgresTestBase {

    @Autowired
    private ReplicatedUserRepository userRepository;
    @Autowired
    private UserReplicationStateRepository stateRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private FeedPostRepository feedPostRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private FeedCommentRepository feedCommentRepository;
    @Autowired
    private EntityManager entityManager;

    private UserProjectionService projectionService;

    @BeforeEach
    void setUp() {
        projectionService = new UserProjectionService(
                userRepository, stateRepository, postRepository, feedPostRepository, commentRepository, feedCommentRepository);
    }

    private WatchUserChangesResponse deletedEvent(UUID userId, long seq) {
        return WatchUserChangesResponse.newBuilder()
                .setUserId(userId.toString())
                .setSequence(seq)
                .setUser(InternalUser.newBuilder().setDeleted(true).setProfilePublic(false).build())
                .build();
    }

    private WatchUserChangesResponse activeEvent(UUID userId, String nickname, long seq) {
        return WatchUserChangesResponse.newBuilder()
                .setUserId(userId.toString())
                .setSequence(seq)
                .setUser(InternalUser.newBuilder().setDeleted(false).setNickname(nickname).setProfilePublic(false).build())
                .build();
    }

    @Test
    @DisplayName("탈퇴 이벤트 반영 시 community_post/feed_post/community_comment/feed_comment 의 작성자 닉네임이 전부 스크럽된다")
    void applyBatch_deletedEvent_scrubsAuthorNicknameEverywhere() {
        UUID userId = UUID.randomUUID();
        CommunityPost savedPost = postRepository.save(CommunityPost.builder()
                .category(Category.FREE)
                .userId(userId)
                .authorNickname("탈퇴전닉네임")
                .title("제목")
                .content("{}")
                .contentText("본문")
                .build());
        FeedPost savedFeedPost = feedPostRepository.save(FeedPost.create(userId, "탈퇴전닉네임", "여행기 제목", "{}", "본문",
                null, "서울", "서울", null, null, 3, null, null, null));
        commentRepository.save(CommunityComment.builder()
                .postId(savedPost.getPostId())
                .userId(userId)
                .authorNickname("탈퇴전닉네임")
                .content("댓글")
                .build());
        feedCommentRepository.save(FeedComment.create(savedFeedPost.getPostId(), userId, "탈퇴전닉네임", "피드댓글", null));

        projectionService.applyBatch(List.of(deletedEvent(userId, 1L)), true);
        entityManager.clear();

        assertThat(postRepository.findByUserIdOrderByCreatedAtDesc(userId, org.springframework.data.domain.Pageable.unpaged())
                .getContent().get(0).getAuthorNickname()).isEqualTo(AuthorProfile.DELETED_NICKNAME);
        assertThat(feedPostRepository.findByUserId(userId, org.springframework.data.domain.Pageable.unpaged())
                .getContent().get(0).getAuthorNickname()).isEqualTo(AuthorProfile.DELETED_NICKNAME);
        assertThat(commentRepository.findByUserIdOrderByCreatedAtDesc(userId, org.springframework.data.domain.Pageable.unpaged())
                .getContent().get(0).getAuthorNickname()).isEqualTo(AuthorProfile.DELETED_NICKNAME);
        assertThat(feedCommentRepository.findByUserIdOrderByCreatedAtDesc(userId, org.springframework.data.domain.Pageable.unpaged())
                .getContent().get(0).getAuthorNickname()).isEqualTo(AuthorProfile.DELETED_NICKNAME);

        assertThat(userRepository.findById(userId).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("탈퇴가 아닌 일반 프로필 갱신 이벤트는 작성자 닉네임 스냅샷을 건드리지 않는다")
    void applyBatch_nonDeletedEvent_doesNotScrub() {
        UUID userId = UUID.randomUUID();
        postRepository.save(CommunityPost.builder()
                .category(Category.FREE)
                .userId(userId)
                .authorNickname("원래닉네임")
                .title("제목")
                .content("{}")
                .contentText("본문")
                .build());

        projectionService.applyBatch(List.of(activeEvent(userId, "바뀐닉네임", 1L)), true);
        entityManager.clear();

        // community_user 의 라이브 닉네임은 갱신되지만, 게시글에 남은 스냅샷은 그대로다
        // (평소엔 라이브 값이 우선이라 화면엔 영향 없음 — 폴백 상황에서만 의미가 있는 값)
        assertThat(postRepository.findByUserIdOrderByCreatedAtDesc(userId, org.springframework.data.domain.Pageable.unpaged())
                .getContent().get(0).getAuthorNickname()).isEqualTo("원래닉네임");
        assertThat(userRepository.findById(userId).orElseThrow().isDeleted()).isFalse();
    }
}
