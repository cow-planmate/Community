package com.planmate.community.domain.post.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.community.common.client.UserClient;
import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.fork.repository.FeedForkRepository;
import com.planmate.community.domain.post.dto.PostCreateRequest;
import com.planmate.community.domain.post.dto.PostDetailResponse;
import com.planmate.community.domain.post.dto.PostSummaryResponse;
import com.planmate.community.domain.post.dto.PostUpdateRequest;
import com.planmate.community.domain.post.dto.RegionCountResponse;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.post.enums.MateStatus;
import com.planmate.community.domain.post.enums.SortType;
import com.planmate.community.domain.post.repository.PostRepository;
import com.planmate.community.domain.post.validator.PostAccessValidator;
import com.planmate.community.domain.reaction.repository.ReactionRepository;
import com.planmate.community.domain.stats.service.UserStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PostRepository postRepository;
    private final UserClient userClient;
    private final PostAccessValidator postAccessValidator;
    private final ObjectMapper objectMapper;
    private final ViewCountService viewCountService;
    private final ReactionRepository reactionRepository;
    private final FeedForkRepository feedForkRepository;
    private final PostAssembler postAssembler;
    private final UserStatsService userStatsService;

    @Transactional
    public PostDetailResponse createPost(UUID userId, PostCreateRequest request) {
        Category category = Category.from(request.category());
        validateCategoryFields(category, request);

        String nickname = userClient.getNickname(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.INTERNAL_SERVER_ERROR, "사용자 정보를 가져올 수 없습니다."));

        Post post = Post.builder()
                .category(category)
                .userId(userId)
                .authorNickname(nickname)
                .title(request.title())
                .content(writeContent(request.content()))
                .contentText(request.contentText() != null ? request.contentText() : "")
                .thumbnailUrl(request.thumbnailUrl())
                .isAnswered(category == Category.QNA ? Boolean.FALSE : null)
                .region(category == Category.MATE || category == Category.FEED ? request.region() : null)
                .maxParticipants(category == Category.MATE ? request.maxParticipants() : null)
                .status(category == Category.MATE ? MateStatus.RECRUITING : null)
                .location(category == Category.RECOMMEND || category == Category.FEED ? request.location() : null)
                .rating(category == Category.RECOMMEND ? request.rating() : null)
                .lat(category == Category.RECOMMEND || category == Category.FEED ? request.lat() : null)
                .lng(category == Category.RECOMMEND || category == Category.FEED ? request.lng() : null)
                .durationDays(category == Category.FEED ? request.durationDays() : null)
                .itinerary(category == Category.FEED ? writeItinerary(request.itinerary()) : null)
                .tags(category == Category.FEED && request.tags() != null && !request.tags().isEmpty()
                        ? writeJson(request.tags()) : null)
                .sourcePlanId(category == Category.FEED ? request.sourcePlanId() : null)
                .build();

        Post saved = postRepository.save(post);
        userStatsService.recordPostCreated(userId);
        return postAssembler.toDetail(saved, null, null);
    }

    public PageResponse<PostSummaryResponse> getPosts(String categoryValue, int page, int size, String sortValue, String q,
                                                      String region, Integer minDays, Integer maxDays, String tag, UUID userId) {
        Category category = Category.from(categoryValue);
        SortType sortType = SortType.from(sortValue);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sortType.toSort());

        // 특정 사용자의 글만 (프로필 페이지) — 다른 필터와 조합하지 않는다
        Page<Post> posts = userId != null
                ? postRepository.findByCategoryAndUserId(category, userId, pageable)
                : findPostsPage(category, normalizeBlank(q), normalizeBlank(region), minDays, maxDays, normalizeBlank(tag), pageable);
        return PageResponse.of(posts, postAssembler.toSummaries(posts.getContent()));
    }

    // FEED에 피드 필터가 하나라도 있으면 전용 쿼리로, 아니면 기존 조회/검색 쿼리로 라우팅한다
    private Page<Post> findPostsPage(Category category, String q, String region, Integer minDays, Integer maxDays, String tag, Pageable pageable) {
        if (category == Category.FEED && (region != null || minDays != null || maxDays != null || tag != null)) {
            return postRepository.findFeedPosts(category, region, minDays, maxDays, tag, q, pageable);
        }
        return q == null
                ? postRepository.findByCategory(category, pageable)
                : postRepository.searchByCategory(category, q, pageable);
    }

    public List<RegionCountResponse> getRegionCounts(String categoryValue) {
        Category category = Category.from(categoryValue);
        return postRepository.countRegionsByCategory(category).stream()
                .map(RegionCountResponse::of)
                .toList();
    }

    public List<PostSummaryResponse> getHotPosts(String categoryValue) {
        Category category = Category.from(categoryValue);
        return postAssembler.toSummaries(postRepository.findTop3ByCategoryOrderByLikeCountDescCreatedAtDesc(category));
    }

    /**
     * 상세 조회 — 조회수 증가(조회자별 24h 중복 방지, Redis 버퍼링으로 응답의 조회수는 최대 flush 주기만큼 지연될 수 있다).
     *
     * @param viewerId  로그인 사용자 id (비로그인 null)
     * @param viewerKey 조회수 중복 방지 키 (로그인: userId, 비로그인: 원격 IP)
     */
    @Transactional
    public PostDetailResponse getPost(Long postId, UUID viewerId, String viewerKey) {
        Post post = findPost(postId);
        viewCountService.registerView(postId, viewerKey);
        return postAssembler.toDetail(post, findMyReaction(postId, viewerId), findMyFork(post, viewerId));
    }

    @Transactional
    public PostDetailResponse updatePost(UUID userId, Long postId, PostUpdateRequest request) {
        Post post = findPost(postId);
        postAccessValidator.validateAuthor(post, userId);

        post.update(
                request.title(),
                request.content() != null ? writeContent(request.content()) : null,
                request.contentText(),
                request.thumbnailUrl()
        );
        if (post.getCategory() == Category.RECOMMEND) {
            if (request.rating() != null) {
                validateRating(request.rating());
            }
            post.updateRecommendFields(request.location(), request.rating(), request.lat(), request.lng());
        }
        if (post.getCategory() == Category.MATE) {
            post.updateMateFields(request.region(), request.maxParticipants());
        }

        return postAssembler.toDetail(post, findMyReaction(postId, userId), findMyFork(post, userId));
    }

    /**
     * QnA 답변 완료 상태 변경 (작성자 전용).
     */
    @Transactional
    public PostDetailResponse updateAnswered(UUID userId, Long postId, boolean answered) {
        Post post = findPost(postId);
        postAccessValidator.validateAuthor(post, userId);
        if (post.getCategory() != Category.QNA) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "QnA 게시판 게시글이 아닙니다.");
        }
        post.markAnswered(answered);
        return postAssembler.toDetail(post, findMyReaction(postId, userId), findMyFork(post, userId));
    }

    @Transactional
    public void deletePost(UUID userId, boolean isAdmin, Long postId) {
        Post post = findPost(postId);
        postAccessValidator.validateAuthorOrAdmin(post, userId, isAdmin);
        post.softDelete();
        userStatsService.recordPostDeleted(post.getUserId());
    }

    private Post findPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.POST_NOT_FOUND));
    }

    private String findMyReaction(Long postId, UUID viewerId) {
        if (viewerId == null) {
            return null;
        }
        return reactionRepository.findByPostIdAndUserId(postId, viewerId)
                .map(reaction -> reaction.getType().toLowerValue())
                .orElse(null);
    }

    // FEED 상세에서 로그인 사용자의 가져가기 여부 (비로그인·비FEED는 null → 응답에서 생략)
    private Boolean findMyFork(Post post, UUID viewerId) {
        if (viewerId == null || post.getCategory() != Category.FEED) {
            return null;
        }
        return feedForkRepository.existsByPostIdAndUserId(post.getPostId(), viewerId);
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateCategoryFields(Category category, PostCreateRequest request) {
        if (category == Category.RECOMMEND) {
            if (request.location() == null || request.location().isBlank()) {
                throw new CommunityException(ErrorCode.INVALID_INPUT, "추천 게시글은 위치 정보가 필수입니다.");
            }
            if (request.rating() == null) {
                throw new CommunityException(ErrorCode.INVALID_INPUT, "추천 게시글은 평점이 필수입니다.");
            }
            validateRating(request.rating());
        }
        if (category == Category.MATE) {
            if (request.region() == null || request.region().isBlank()) {
                throw new CommunityException(ErrorCode.INVALID_INPUT, "메이트 게시글은 지역 정보가 필수입니다.");
            }
            if (request.maxParticipants() != null && request.maxParticipants() < 2) {
                throw new CommunityException(ErrorCode.INVALID_INPUT, "모집 인원은 2명 이상이어야 합니다.");
            }
        }
        if (category == Category.FEED) {
            if (request.region() == null || request.region().isBlank()) {
                throw new CommunityException(ErrorCode.INVALID_INPUT, "피드 게시글은 지역 정보가 필수입니다.");
            }
            if (request.durationDays() == null || request.durationDays() < 1) {
                throw new CommunityException(ErrorCode.INVALID_INPUT, "피드 게시글은 1일 이상의 여행 기간이 필수입니다.");
            }
            validateItinerary(request.itinerary());
        }
    }

    // itinerary 구조 검증 (선택 필드) — days는 비어있지 않은 배열, 각 항목에 time·place 필수
    private void validateItinerary(JsonNode itinerary) {
        if (itinerary == null || itinerary.isNull()) {
            return;
        }
        JsonNode days = itinerary.get("days");
        if (days == null || !days.isArray() || days.isEmpty()) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "일정에는 비어있지 않은 days 배열이 필요합니다.");
        }
        for (JsonNode day : days) {
            JsonNode items = day.get("items");
            if (items == null) {
                continue;
            }
            if (!items.isArray()) {
                throw new CommunityException(ErrorCode.INVALID_INPUT, "일정의 items는 배열이어야 합니다.");
            }
            for (JsonNode item : items) {
                if (isBlankText(item.get("time")) || isBlankText(item.get("place"))) {
                    throw new CommunityException(ErrorCode.INVALID_INPUT, "일정 항목에는 time과 place가 필수입니다.");
                }
            }
        }
    }

    private boolean isBlankText(JsonNode node) {
        return node == null || !node.isTextual() || node.asText().isBlank();
    }

    private void validateRating(BigDecimal rating) {
        if (rating.compareTo(BigDecimal.ZERO) < 0 || rating.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "평점은 0.0에서 5.0 사이여야 합니다.");
        }
    }

    private String writeContent(JsonNode content) {
        return writeJson(content);
    }

    private String writeItinerary(JsonNode itinerary) {
        return itinerary == null || itinerary.isNull() ? null : writeJson(itinerary);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "내용 형식이 올바르지 않습니다.");
        }
    }
}
