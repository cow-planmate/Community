package com.planmate.community.domain.post.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.community.common.client.UserClient;
import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.post.dto.PostCreateRequest;
import com.planmate.community.domain.post.dto.PostUpdateRequest;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.post.enums.MateStatus;
import com.planmate.community.domain.post.repository.PostRepository;
import com.planmate.community.domain.post.validator.PostAccessValidator;
import com.planmate.community.domain.stats.repository.UserStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserStatsRepository userStatsRepository;

    @Mock
    private UserClient userClient;

    private PostService postService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        postService = new PostService(
                postRepository, userStatsRepository, userClient, new PostAccessValidator(), objectMapper);
    }

    private PostCreateRequest createRequest(String category, String location, BigDecimal rating, String region, Integer maxParticipants) {
        return new PostCreateRequest(
                category, "제목", objectMapper.createObjectNode(), "본문 텍스트", null,
                location, rating, null, null, region, maxParticipants);
    }

    @Test
    @DisplayName("추천 게시글은 위치가 없으면 INVALID_INPUT 예외가 발생한다")
    void createRecommendPostWithoutLocation() {
        assertThatThrownBy(() -> postService.createPost(userId, createRequest("recommend", null, BigDecimal.valueOf(4.5), null, null)))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT))
                .hasMessageContaining("위치");
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("메이트 게시글 생성 시 모집중 상태와 닉네임 스냅샷이 저장된다")
    void createMatePostDefaults() {
        when(userClient.getNickname(userId)).thenReturn(Optional.of("여행자"));
        when(userStatsRepository.findById(userId)).thenReturn(Optional.empty());
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            ReflectionTestUtils.setField(post, "postId", 1L);
            return post;
        });

        var response = postService.createPost(userId, createRequest("mate", null, null, "제주", 4));

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        Post saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(MateStatus.RECRUITING);
        assertThat(saved.getAuthorNickname()).isEqualTo("여행자");
        assertThat(saved.getRegion()).isEqualTo("제주");
        assertThat(saved.getIsAnswered()).isNull();
        assertThat(response.level()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("recruiting");
    }

    @Test
    @DisplayName("사용자 정보를 가져올 수 없으면 게시글 생성이 실패한다")
    void createPostWithoutUserInfo() {
        when(userClient.getNickname(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.createPost(userId, createRequest("free", null, null, null, null)))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("작성자가 아니면 게시글 수정이 거부된다")
    void updatePostByNonAuthor() {
        Post post = freePost(userId);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        UUID otherUser = UUID.randomUUID();
        PostUpdateRequest request = new PostUpdateRequest("새 제목", null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> postService.updatePost(otherUser, 1L, request))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));
    }

    @Test
    @DisplayName("관리자는 작성자가 아니어도 게시글을 삭제(soft delete)할 수 있다")
    void deletePostByAdmin() {
        Post post = freePost(userId);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        postService.deletePost(UUID.randomUUID(), true, 1L);

        assertThat(post.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 게시글 조회 시 POST_NOT_FOUND 예외가 발생한다")
    void getPostNotFound() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPost(99L))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.POST_NOT_FOUND));
    }

    @Test
    @DisplayName("검색어가 있으면 검색 쿼리를, 없으면 카테고리 조회를 사용한다")
    void getPostsQueryRouting() {
        when(postRepository.findByCategory(eq(Category.FREE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(postRepository.searchByCategory(eq(Category.FREE), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(userClient.getNicknames(anyCollection())).thenReturn(Map.of());
        when(userStatsRepository.findAllById(any())).thenReturn(List.of());

        postService.getPosts("free", 0, 20, "latest", null);
        verify(postRepository).findByCategory(eq(Category.FREE), any(Pageable.class));

        postService.getPosts("free", 0, 20, "latest", "맛집");
        verify(postRepository).searchByCategory(eq(Category.FREE), eq("맛집"), any(Pageable.class));
    }

    private Post freePost(UUID authorId) {
        Post post = Post.builder()
                .category(Category.FREE)
                .userId(authorId)
                .authorNickname("작성자")
                .title("제목")
                .content("{}")
                .contentText("본문")
                .build();
        ReflectionTestUtils.setField(post, "postId", 1L);
        return post;
    }
}
