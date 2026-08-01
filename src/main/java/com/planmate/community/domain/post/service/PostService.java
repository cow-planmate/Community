package com.planmate.community.domain.post.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.community.common.access.ProfileAccessValidator;
import com.planmate.community.common.client.AuthorProfile;
import com.planmate.community.common.client.UserClient;
import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.fork.repository.FeedForkRepository;
import com.planmate.community.domain.image.service.ImageService;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Pattern HH_MM = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private final PostRepository postRepository;
    private final UserClient userClient;
    private final PostAccessValidator postAccessValidator;
    private final ProfileAccessValidator profileAccessValidator;
    private final ObjectMapper objectMapper;
    private final ViewCountService viewCountService;
    private final ReactionRepository reactionRepository;
    private final FeedForkRepository feedForkRepository;
    private final PostAssembler postAssembler;
    private final UserStatsService userStatsService;
    private final ImageService imageService;

    @Transactional
    public PostDetailResponse createPost(UUID userId, PostCreateRequest request) {
        Category category = Category.from(request.category());
        validateCategoryFields(category, request);

        AuthorProfile author = userClient.getAuthor(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.INTERNAL_SERVER_ERROR, "사용자 정보를 가져올 수 없습니다."));

        Post post = Post.builder()
                .category(category)
                .userId(userId)
                .authorNickname(author.nickname())
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

    /**
     * @param viewerId 로그인 사용자 id (비로그인 null) — 작성자별 목록의 공개 여부 판단에만 쓴다
     */
    public PageResponse<PostSummaryResponse> getPosts(String categoryValue, int page, int size, String sortValue,
                                                      String orderValue, String q,
                                                      String region, Integer minDays, Integer maxDays, String tag,
                                                      UUID userId, UUID viewerId) {
        Category category = Category.from(categoryValue);
        SortType sortType = SortType.from(sortValue);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                sortType.toSort(SortType.direction(orderValue)));

        // 작성자별 목록은 프로필의 일부다 — 비공개 프로필이면 본인 외에는 목록도 볼 수 없다.
        // (게시판 전체 목록에는 이 사용자의 글이 계속 보인다. 감추는 건 "누가 썼는지로 모아보는" 경로다)
        if (userId != null) {
            profileAccessValidator.validateVisible(userId, viewerId);
        }

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
     * 상세 조회 — 조회 요청마다 조회수 증가(Redis 버퍼링으로 응답의 조회수는 최대 flush 주기만큼 지연될 수 있다).
     *
     * @param viewerId 로그인 사용자 id (비로그인 null)
     */
    @Transactional
    public PostDetailResponse getPost(Long postId, UUID viewerId) {
        Post post = findPost(postId);
        viewCountService.registerView(postId);
        return postAssembler.toDetail(post, findMyReaction(postId, viewerId), findMyFork(post, viewerId));
    }

    @Transactional
    public PostDetailResponse updatePost(UUID userId, Long postId, PostUpdateRequest request) {
        Post post = findPost(postId);
        postAccessValidator.validateAuthor(post, userId);

        // 수정 전 이미지 URL(본문 + 커버)을 기록해 두었다가, 수정 후 더 이상 참조되지 않는 것만 정리한다
        Set<String> previousImageUrls = collectImageUrls(post);

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
        if (post.getCategory() == Category.FEED) {
            if (request.durationDays() != null && request.durationDays() < 1) {
                throw new CommunityException(ErrorCode.INVALID_INPUT, "피드 게시글은 1일 이상의 여행 기간이 필수입니다.");
            }
            validateItinerary(request.itinerary());
            // itinerary/tags는 필드가 넘어온 경우에만 반영한다 (null 전달 = 비우기)
            boolean itineraryChanged = request.itinerary() != null;
            boolean tagsChanged = request.tags() != null;
            post.updateFeedFields(
                    request.region(),
                    request.location(),
                    request.durationDays(),
                    itineraryChanged ? writeItinerary(request.itinerary()) : null,
                    itineraryChanged,
                    tagsChanged && !request.tags().isEmpty() ? writeJson(request.tags()) : null,
                    tagsChanged
            );
        }

        // 본문에서 빠진 이미지 + 교체된 커버 이미지를 MinIO에서 정리한다.
        // 커버가 본문에도 쓰이는 경우(또는 그 반대)가 있으므로 양쪽을 합쳐 비교해야 살아있는 이미지를 지우지 않는다.
        previousImageUrls.removeAll(collectImageUrls(post));
        deleteImagesAfterCommit(previousImageUrls);

        return postAssembler.toDetail(post, findMyReaction(postId, userId), findMyFork(post, userId));
    }

    /** 게시글이 현재 참조하는 이미지 URL 집합 (본문 이미지 블록 + 커버). */
    private Set<String> collectImageUrls(Post post) {
        Set<String> urls = imageService.extractImageUrls(post.getContent());
        if (post.getThumbnailUrl() != null) {
            urls.add(post.getThumbnailUrl());
        }
        return urls;
    }

    /**
     * 이미지 삭제는 커밋 이후로 미룬다 — 트랜잭션이 롤백되면 글은 그대로인데 이미지만 사라지는 상태를 막는다.
     * 삭제 자체는 best-effort라 실패해도 트랜잭션 결과에 영향을 주지 않는다.
     */
    private void deleteImagesAfterCommit(Collection<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        Set<String> targets = Set.copyOf(urls);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            imageService.deleteAll(targets);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                imageService.deleteAll(targets);
            }
        });
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
        // 삭제된 글의 본문/썸네일 이미지를 MinIO에서 정리한다 (고아 방지, best-effort)
        deleteImagesAfterCommit(collectImageUrls(post));
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
    //
    // itinerary는 "가져가기"로 다른 사용자의 플랜을 만들어내는 원본 스냅샷이다.
    // 상위 plan 객체(destinationId 등)가 있어야 Backend-v2의 POST /api/plan/full로 복제할 수 있지만,
    // 플랜 없이 손으로 쓴 여행기와 구 스키마 게시글도 허용해야 하므로 plan은 선택 필드로 둔다.
    // (plan이 없는 글은 프론트에서 가져가기 버튼이 비활성화된다)
    private void validateItinerary(JsonNode itinerary) {
        if (itinerary == null || itinerary.isNull()) {
            return;
        }
        validatePlanSnapshot(itinerary.get("plan"));

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
                validateItemTimeRange(item);
            }
        }
    }

    private void validatePlanSnapshot(JsonNode plan) {
        if (plan == null || plan.isNull()) {
            return;
        }
        if (!plan.isObject()) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "일정의 plan은 객체여야 합니다.");
        }
        JsonNode destinationId = plan.get("destinationId");
        if (destinationId == null || !destinationId.isIntegralNumber()) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "일정의 plan에는 destinationId가 필요합니다.");
        }
        if (isBlankText(plan.get("transportationType"))) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "일정의 plan에는 transportationType이 필요합니다.");
        }
        validateNonNegativeCount(plan.get("adultCount"), "adultCount");
        validateNonNegativeCount(plan.get("childCount"), "childCount");
    }

    private void validateNonNegativeCount(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isIntegralNumber() || node.asInt() < 0) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "일정의 plan." + field + "은 0 이상의 정수여야 합니다.");
        }
    }

    // endTime은 구 스키마에는 없으므로 선택이지만, 있으면 HH:mm 형식이고 time보다 이르면 안 된다
    private void validateItemTimeRange(JsonNode item) {
        JsonNode endTime = item.get("endTime");
        if (endTime == null || endTime.isNull()) {
            return;
        }
        if (isBlankText(endTime) || !HH_MM.matcher(endTime.asText()).matches()) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "일정 항목의 endTime은 HH:mm 형식이어야 합니다.");
        }
        String time = item.get("time").asText();
        if (HH_MM.matcher(time).matches() && endTime.asText().compareTo(time) < 0) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "일정 항목의 endTime은 time보다 이를 수 없습니다.");
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
