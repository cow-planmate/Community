package com.planmate.community.domain.post.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.planmate.community.common.client.UserClient;
import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.fork.repository.FeedForkRepository;
import com.planmate.community.domain.participant.repository.MateParticipantRepository;
import com.planmate.community.domain.post.dto.PostCreateRequest;
import com.planmate.community.domain.post.dto.PostUpdateRequest;
import com.planmate.community.domain.post.dto.RegionCountResponse;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.post.enums.MateStatus;
import com.planmate.community.domain.post.repository.PostRepository;
import com.planmate.community.domain.post.validator.PostAccessValidator;
import com.planmate.community.domain.reaction.repository.ReactionRepository;
import com.planmate.community.domain.stats.repository.UserStatsRepository;
import com.planmate.community.domain.stats.service.UserStatsService;
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
import static org.mockito.ArgumentMatchers.isNull;
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
    private MateParticipantRepository mateParticipantRepository;

    @Mock
    private UserClient userClient;

    @Mock
    private ViewCountService viewCountService;

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private FeedForkRepository feedForkRepository;

    @Mock
    private UserStatsService userStatsService;

    private PostService postService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PostAssembler postAssembler = new PostAssembler(
                userClient, userStatsRepository, mateParticipantRepository, objectMapper);
        postService = new PostService(
                postRepository, userClient, new PostAccessValidator(), objectMapper,
                viewCountService, reactionRepository, feedForkRepository, postAssembler, userStatsService);
    }

    private PostCreateRequest createRequest(String category, String location, BigDecimal rating, String region, Integer maxParticipants) {
        return new PostCreateRequest(
                category, "제목", objectMapper.createObjectNode(), "본문 텍스트", null,
                location, rating, null, null, region, maxParticipants,
                null, null, null, null);
    }

    private PostCreateRequest feedRequest(String region, Integer durationDays, JsonNode itinerary, List<String> tags) {
        return new PostCreateRequest(
                "feed", "제목", objectMapper.createObjectNode(), "본문 텍스트", null,
                null, null, null, null, region, null,
                durationDays, itinerary, tags, null);
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
    @DisplayName("메이트 게시글 생성 시 모집중 상태·닉네임 스냅샷·활동 통계 기록이 수행된다")
    void createMatePostDefaults() {
        when(userClient.getNickname(userId)).thenReturn(Optional.of("여행자"));
        when(userStatsRepository.findById(userId)).thenReturn(Optional.empty());
        when(mateParticipantRepository.countByPostId(1L)).thenReturn(0L);
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
        verify(userStatsService).recordPostCreated(userId);
        assertThat(response.level()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("recruiting");
        assertThat(response.participants()).isZero();
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
    @DisplayName("관리자는 작성자가 아니어도 게시글을 삭제할 수 있고 작성자 통계가 감소한다")
    void deletePostByAdmin() {
        Post post = freePost(userId);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        postService.deletePost(UUID.randomUUID(), true, 1L);

        assertThat(post.isDeleted()).isTrue();
        verify(userStatsService).recordPostDeleted(userId);
    }

    @Test
    @DisplayName("QnA가 아닌 게시글에 답변 완료 표시를 하면 INVALID_INPUT 예외가 발생한다")
    void updateAnsweredOnNonQnaPost() {
        Post post = freePost(userId);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.updateAnswered(userId, 1L, true))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("존재하지 않는 게시글 조회 시 POST_NOT_FOUND 예외가 발생한다")
    void getPostNotFound() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPost(99L, null, "127.0.0.1"))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.POST_NOT_FOUND));
        verify(viewCountService, never()).registerView(any(), anyString());
    }

    @Test
    @DisplayName("상세 조회 시 조회수 등록과 myReaction 조회가 수행된다")
    void getPostRegistersViewAndMyReaction() {
        Post post = freePost(userId);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(userClient.getNickname(userId)).thenReturn(Optional.of("최신닉네임"));
        when(userStatsRepository.findById(userId)).thenReturn(Optional.empty());
        when(reactionRepository.findByPostIdAndUserId(1L, userId)).thenReturn(Optional.empty());

        var response = postService.getPost(1L, userId, userId.toString());

        verify(viewCountService).registerView(1L, userId.toString());
        assertThat(response.author()).isEqualTo("최신닉네임");
        assertThat(response.myReaction()).isNull();
        assertThat(response.myFork()).isNull();
        verify(feedForkRepository, never()).existsByPostIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("피드 상세 조회 시 로그인 사용자가 가져갔으면 myFork가 true다")
    void getFeedPostWithMyForkTrue() {
        Post post = feedPost(userId);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(userClient.getNickname(userId)).thenReturn(Optional.of("여행자"));
        when(userStatsRepository.findById(userId)).thenReturn(Optional.empty());
        when(reactionRepository.findByPostIdAndUserId(1L, userId)).thenReturn(Optional.empty());
        when(feedForkRepository.existsByPostIdAndUserId(1L, userId)).thenReturn(true);

        var response = postService.getPost(1L, userId, userId.toString());

        assertThat(response.myFork()).isTrue();
    }

    @Test
    @DisplayName("피드 상세 조회 시 가져가지 않았으면 myFork가 false, 비로그인이면 null이다")
    void getFeedPostWithMyForkFalseOrNull() {
        Post post = feedPost(userId);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(userClient.getNickname(userId)).thenReturn(Optional.of("여행자"));
        when(userStatsRepository.findById(userId)).thenReturn(Optional.empty());
        when(feedForkRepository.existsByPostIdAndUserId(1L, userId)).thenReturn(false);

        var authenticated = postService.getPost(1L, userId, userId.toString());
        assertThat(authenticated.myFork()).isFalse();

        var anonymous = postService.getPost(1L, null, "127.0.0.1");
        assertThat(anonymous.myFork()).isNull();
        verify(feedForkRepository).existsByPostIdAndUserId(1L, userId);
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

        postService.getPosts("free", 0, 20, "latest", null, null, null, null, null, null);
        verify(postRepository).findByCategory(eq(Category.FREE), any(Pageable.class));

        postService.getPosts("free", 0, 20, "latest", "맛집", null, null, null, null, null);
        verify(postRepository).searchByCategory(eq(Category.FREE), eq("맛집"), any(Pageable.class));
    }

    @Test
    @DisplayName("피드 게시글은 지역이 없으면 INVALID_INPUT 예외가 발생한다")
    void createFeedPostWithoutRegion() {
        assertThatThrownBy(() -> postService.createPost(userId, feedRequest(null, 3, null, null)))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT))
                .hasMessageContaining("지역");
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("피드 게시글은 여행 기간이 없거나 1일 미만이면 INVALID_INPUT 예외가 발생한다")
    void createFeedPostWithInvalidDuration() {
        assertThatThrownBy(() -> postService.createPost(userId, feedRequest("서울", null, null, null)))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT))
                .hasMessageContaining("기간");
        assertThatThrownBy(() -> postService.createPost(userId, feedRequest("서울", 0, null, null)))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("피드 일정의 days가 비어 있으면 INVALID_INPUT 예외가 발생한다")
    void createFeedPostWithEmptyItineraryDays() {
        ObjectNode itinerary = objectMapper.createObjectNode();
        itinerary.putArray("days");

        assertThatThrownBy(() -> postService.createPost(userId, feedRequest("서울", 3, itinerary, null)))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT))
                .hasMessageContaining("days");
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("피드 일정 항목에 time이나 place가 없으면 INVALID_INPUT 예외가 발생한다")
    void createFeedPostWithInvalidItineraryItem() {
        ObjectNode itinerary = objectMapper.createObjectNode();
        ObjectNode day = itinerary.putArray("days").addObject();
        day.put("day", 1);
        day.putArray("items").addObject().put("time", "10:00"); // place 누락

        assertThatThrownBy(() -> postService.createPost(userId, feedRequest("서울", 3, itinerary, null)))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT))
                .hasMessageContaining("place");
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("피드 게시글 생성 시 피드 필드가 직렬화되어 저장되고 응답에 반영된다")
    void createFeedPostStoresFeedFields() throws Exception {
        when(userClient.getNickname(userId)).thenReturn(Optional.of("여행자"));
        when(userStatsRepository.findById(userId)).thenReturn(Optional.empty());
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            ReflectionTestUtils.setField(post, "postId", 1L);
            return post;
        });

        ObjectNode itinerary = objectMapper.createObjectNode();
        ObjectNode day = itinerary.putArray("days").addObject();
        day.put("day", 1);
        ObjectNode item = day.putArray("items").addObject();
        item.put("time", "10:00");
        item.put("place", "경복궁");

        var response = postService.createPost(userId, feedRequest("서울", 3, itinerary, List.of("#극한의J")));

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        Post saved = captor.getValue();
        assertThat(saved.getRegion()).isEqualTo("서울");
        assertThat(saved.getDurationDays()).isEqualTo(3);
        assertThat(saved.getItinerary()).isEqualTo(objectMapper.writeValueAsString(itinerary));
        assertThat(saved.getTags()).isEqualTo(objectMapper.writeValueAsString(List.of("#극한의J")));
        assertThat(saved.getForkCount()).isZero();
        verify(userStatsService).recordPostCreated(userId);
        assertThat(response.durationDays()).isEqualTo(3);
        assertThat(response.forks()).isZero();
        assertThat(response.tags()).containsExactly("#극한의J");
        assertThat(response.itinerary()).isEqualTo(itinerary);
        assertThat(response.myFork()).isNull();
    }

    @Test
    @DisplayName("피드가 아닌 게시글 생성 시 피드 전용 필드는 null로 저장된다")
    void createNonFeedPostIgnoresFeedFields() {
        when(userClient.getNickname(userId)).thenReturn(Optional.of("여행자"));
        when(userStatsRepository.findById(userId)).thenReturn(Optional.empty());
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            ReflectionTestUtils.setField(post, "postId", 1L);
            return post;
        });

        PostCreateRequest request = new PostCreateRequest(
                "free", "제목", objectMapper.createObjectNode(), "본문 텍스트", null,
                null, null, null, null, null, null,
                3, objectMapper.createObjectNode(), List.of("#태그"), UUID.randomUUID());

        var response = postService.createPost(userId, request);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        Post saved = captor.getValue();
        assertThat(saved.getDurationDays()).isNull();
        assertThat(saved.getItinerary()).isNull();
        assertThat(saved.getTags()).isNull();
        assertThat(saved.getSourcePlanId()).isNull();
        assertThat(response.durationDays()).isNull();
        assertThat(response.forks()).isNull();
        assertThat(response.tags()).isNull();
        assertThat(response.itinerary()).isNull();
    }

    @Test
    @DisplayName("피드에 필터가 있으면 전용 쿼리를, 없으면 카테고리 조회를 사용한다")
    void getFeedPostsQueryRouting() {
        when(postRepository.findByCategory(eq(Category.FEED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(postRepository.findFeedPosts(eq(Category.FEED), eq("서울"), eq(2), eq(3), eq("#극한의J"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(userClient.getNicknames(anyCollection())).thenReturn(Map.of());
        when(userStatsRepository.findAllById(any())).thenReturn(List.of());

        postService.getPosts("feed", 0, 20, "forks", null, null, null, null, null, null);
        verify(postRepository).findByCategory(eq(Category.FEED), any(Pageable.class));
        verify(postRepository, never()).findFeedPosts(any(), any(), any(), any(), any(), any(), any());

        postService.getPosts("feed", 0, 20, "forks", null, "서울", 2, 3, "#극한의J", null);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findFeedPosts(eq(Category.FEED), eq("서울"), eq(2), eq(3), eq("#극한의J"), isNull(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("forkCount")).isNotNull();
    }

    @Test
    @DisplayName("지역 집계는 리포지토리 결과를 응답 DTO로 매핑한다")
    void getRegionCounts() {
        PostRepository.RegionCount seoul = new PostRepository.RegionCount() {
            @Override
            public String getRegion() {
                return "서울";
            }

            @Override
            public long getPostCount() {
                return 3;
            }
        };
        when(postRepository.countRegionsByCategory(Category.FEED)).thenReturn(List.of(seoul));

        assertThat(postService.getRegionCounts("feed"))
                .containsExactly(new RegionCountResponse("서울", 3));
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

    private Post feedPost(UUID authorId) {
        Post post = Post.builder()
                .category(Category.FEED)
                .userId(authorId)
                .authorNickname("작성자")
                .title("서울 여행")
                .content("{}")
                .contentText("본문")
                .region("서울")
                .durationDays(3)
                .build();
        ReflectionTestUtils.setField(post, "postId", 1L);
        return post;
    }
}
