package com.planmate.community.domain.stats.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.community.common.client.AuthorProfile;
import com.planmate.community.common.client.UserClient;
import com.planmate.community.domain.comment.repository.CommentRepository;
import com.planmate.community.domain.participant.repository.MateParticipantRepository;
import com.planmate.community.domain.post.dto.PostSummaryResponse;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.post.repository.PostRepository;
import com.planmate.community.domain.post.service.PostAssembler;
import com.planmate.community.domain.reaction.entity.Reaction;
import com.planmate.community.domain.reaction.enums.ReactionType;
import com.planmate.community.domain.reaction.repository.ReactionRepository;
import com.planmate.community.domain.stats.repository.UserStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyActivityServiceTest {

    // 프로필 사진 없는 작성자 — 아이콘은 클라이언트가 이니셜로 그린다
    private static AuthorProfile author(String nickname) {
        return new AuthorProfile(nickname, null, null, false);
    }


    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private UserStatsRepository userStatsRepository;

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private UserClient userClient;

    @Mock
    private MateParticipantRepository mateParticipantRepository;

    private MyActivityService myActivityService;

    private final UUID userId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PostAssembler postAssembler = new PostAssembler(
                userClient, userStatsRepository, mateParticipantRepository, new ObjectMapper());
        myActivityService = new MyActivityService(
                postRepository, commentRepository, userStatsRepository,
                reactionRepository, userClient, postAssembler);
    }

    private Post feedPost(long postId, String title) {
        Post post = Post.builder()
                .category(Category.FEED)
                .userId(authorId)
                .authorNickname("작성자")
                .title(title)
                .content("{}")
                .contentText("본문")
                .region("서울")
                .durationDays(3)
                .build();
        ReflectionTestUtils.setField(post, "postId", postId);
        return post;
    }

    private void stubAssembler() {
        when(userClient.getAuthors(anyCollection())).thenReturn(Map.of());
        when(userStatsRepository.findAllById(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("category를 주면 해당 게시판의 내 글만 조회한다")
    void getMyPostsWithCategory() {
        stubAssembler();
        when(postRepository.findByUserIdAndCategoryInOrderByCreatedAtDesc(eq(userId), eq(List.of(Category.FEED)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(feedPost(1L, "서울 여행"))));

        var response = myActivityService.getMyPosts(userId, "feed", 0, 20);

        assertThat(response.items()).extracting(PostSummaryResponse::title).containsExactly("서울 여행");
        verify(postRepository, never()).findByUserIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("쉼표로 구분된 여러 category를 조회할 수 있다")
    void getMyPostsWithMultipleCategories() {
        stubAssembler();
        when(postRepository.findByUserIdAndCategoryInOrderByCreatedAtDesc(
                eq(userId), eq(List.of(Category.FREE, Category.QNA, Category.MATE, Category.RECOMMEND)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        myActivityService.getMyPosts(userId, "free,qna,mate,recommend", 0, 20);

        verify(postRepository, never()).findByUserIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("category가 없으면 게시판 구분 없이 내 글을 조회한다")
    void getMyPostsWithoutCategory() {
        stubAssembler();
        when(postRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(feedPost(1L, "서울 여행"))));

        myActivityService.getMyPosts(userId, null, 0, 20);

        verify(postRepository, never()).findByUserIdAndCategoryInOrderByCreatedAtDesc(any(), any(), any());
    }

    @Test
    @DisplayName("좋아요한 글에는 좋아요한 시각(actedAt)이 채워진다")
    void getLikedPostsFillsActedAt() {
        stubAssembler();
        LocalDateTime likedAt = LocalDateTime.of(2026, 7, 20, 10, 0);
        when(postRepository.findLikedByUserIdAndCategoryIn(eq(userId), eq(List.of(Category.FEED)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(feedPost(1L, "서울 여행"))));
        when(reactionRepository.findByUserIdAndPostIdIn(userId, List.of(1L)))
                .thenReturn(List.of(Reaction.builder()
                        .postId(1L).userId(userId).type(ReactionType.LIKE).createdAt(likedAt).build()));

        var response = myActivityService.getLikedPosts(userId, "feed", 0, 20);

        assertThat(response.items()).singleElement()
                .extracting(PostSummaryResponse::actedAt).isEqualTo(likedAt);
    }
}
