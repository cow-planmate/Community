package com.planmate.community.domain.post.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.community.common.client.UserClient;
import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.post.dto.PostCreateRequest;
import com.planmate.community.domain.post.dto.PostDetailResponse;
import com.planmate.community.domain.post.dto.PostSummaryResponse;
import com.planmate.community.domain.post.dto.PostUpdateRequest;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.post.enums.MateStatus;
import com.planmate.community.domain.post.enums.SortType;
import com.planmate.community.domain.post.repository.PostRepository;
import com.planmate.community.domain.post.validator.PostAccessValidator;
import com.planmate.community.domain.reaction.repository.ReactionRepository;
import com.planmate.community.domain.stats.entity.UserStats;
import com.planmate.community.domain.stats.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PostRepository postRepository;
    private final UserStatsRepository userStatsRepository;
    private final UserClient userClient;
    private final PostAccessValidator postAccessValidator;
    private final ObjectMapper objectMapper;
    private final ViewCountService viewCountService;
    private final ReactionRepository reactionRepository;

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
                .region(category == Category.MATE ? request.region() : null)
                .maxParticipants(category == Category.MATE ? request.maxParticipants() : null)
                .status(category == Category.MATE ? MateStatus.RECRUITING : null)
                .location(category == Category.RECOMMEND ? request.location() : null)
                .rating(category == Category.RECOMMEND ? request.rating() : null)
                .lat(category == Category.RECOMMEND ? request.lat() : null)
                .lng(category == Category.RECOMMEND ? request.lng() : null)
                .build();

        Post saved = postRepository.save(post);
        return PostDetailResponse.of(saved, nickname, findLevel(userId), request.content(), null);
    }

    public PageResponse<PostSummaryResponse> getPosts(String categoryValue, int page, int size, String sortValue, String q) {
        Category category = Category.from(categoryValue);
        SortType sortType = SortType.from(sortValue);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sortType.toSort());

        Page<Post> posts = (q == null || q.isBlank())
                ? postRepository.findByCategory(category, pageable)
                : postRepository.searchByCategory(category, q.trim(), pageable);

        return PageResponse.of(posts, toSummaries(posts.getContent()));
    }

    public List<PostSummaryResponse> getHotPosts(String categoryValue) {
        Category category = Category.from(categoryValue);
        return toSummaries(postRepository.findTop3ByCategoryOrderByLikeCountDescCreatedAtDesc(category));
    }

    /**
     * 상세 조회 — 조회수 증가(조회자별 24h 중복 방지) 후 최신 상태를 반환한다.
     *
     * @param viewerId  로그인 사용자 id (비로그인 null)
     * @param viewerKey 조회수 중복 방지 키 (로그인: userId, 비로그인: 원격 IP)
     */
    @Transactional
    public PostDetailResponse getPost(Long postId, UUID viewerId, String viewerKey) {
        if (!postRepository.existsById(postId)) {
            throw new CommunityException(ErrorCode.POST_NOT_FOUND);
        }
        viewCountService.registerView(postId, viewerKey);

        Post post = findPost(postId);
        String freshNickname = userClient.getNickname(post.getUserId()).orElse(null);
        String myReaction = viewerId == null ? null
                : reactionRepository.findByPostIdAndUserId(postId, viewerId)
                        .map(reaction -> reaction.getType().toLowerValue())
                        .orElse(null);
        return PostDetailResponse.of(post, freshNickname, findLevel(post.getUserId()), readContent(post.getContent()), myReaction);
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
            post.updateRecommendFields(request.location(), request.rating(), request.lat(), request.lng());
        }
        if (post.getCategory() == Category.MATE) {
            post.updateMateFields(request.region(), request.maxParticipants());
        }

        String freshNickname = userClient.getNickname(post.getUserId()).orElse(null);
        String myReaction = reactionRepository.findByPostIdAndUserId(postId, userId)
                .map(reaction -> reaction.getType().toLowerValue())
                .orElse(null);
        return PostDetailResponse.of(post, freshNickname, findLevel(post.getUserId()), readContent(post.getContent()), myReaction);
    }

    @Transactional
    public void deletePost(UUID userId, boolean isAdmin, Long postId) {
        Post post = findPost(postId);
        postAccessValidator.validateAuthorOrAdmin(post, userId, isAdmin);
        post.softDelete();
    }

    private Post findPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.POST_NOT_FOUND));
    }

    private List<PostSummaryResponse> toSummaries(List<Post> posts) {
        List<UUID> userIds = posts.stream().map(Post::getUserId).distinct().toList();
        Map<UUID, String> freshNicknames = userClient.getNicknames(userIds);
        Map<UUID, Integer> levels = userStatsRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserStats::getUserId, UserStats::getLevel));

        return posts.stream()
                .map(post -> PostSummaryResponse.of(
                        post,
                        freshNicknames.get(post.getUserId()),
                        levels.getOrDefault(post.getUserId(), 1)))
                .toList();
    }

    private int findLevel(UUID userId) {
        return userStatsRepository.findById(userId).map(UserStats::getLevel).orElse(1);
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
    }

    private void validateRating(BigDecimal rating) {
        if (rating.compareTo(BigDecimal.ZERO) < 0 || rating.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "평점은 0.0에서 5.0 사이여야 합니다.");
        }
    }

    private String writeContent(JsonNode content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "내용 형식이 올바르지 않습니다.");
        }
    }

    private JsonNode readContent(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException e) {
            throw new CommunityException(ErrorCode.INTERNAL_SERVER_ERROR, "내용을 읽을 수 없습니다.");
        }
    }
}
