package com.planmate.community.domain.stats.service;

import com.planmate.community.common.client.UserClient;
import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.domain.comment.dto.CommentResponse;
import com.planmate.community.domain.comment.entity.Comment;
import com.planmate.community.domain.comment.repository.CommentRepository;
import com.planmate.community.domain.fork.entity.FeedFork;
import com.planmate.community.domain.fork.repository.FeedForkRepository;
import com.planmate.community.domain.post.dto.PostSummaryResponse;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.post.repository.PostRepository;
import com.planmate.community.domain.post.service.PostAssembler;
import com.planmate.community.domain.reaction.entity.Reaction;
import com.planmate.community.domain.reaction.repository.ReactionRepository;
import com.planmate.community.domain.stats.dto.MyStatsResponse;
import com.planmate.community.domain.stats.entity.UserStats;
import com.planmate.community.domain.stats.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyActivityService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserStatsRepository userStatsRepository;
    private final ReactionRepository reactionRepository;
    private final FeedForkRepository feedForkRepository;
    private final UserClient userClient;
    private final PostAssembler postAssembler;

    /**
     * 내가 쓴 글 — category를 주면 해당 게시판만 조회한다.
     * 마이페이지는 여행기(feed)와 커뮤니티 활동을 분리해서 쓰므로 쉼표로 여러 게시판을 넘길 수 있다.
     */
    public PageResponse<PostSummaryResponse> getMyPosts(UUID userId, String categoryValue, int page, int size) {
        Pageable pageable = pageable(page, size);
        List<Category> categories = parseCategories(categoryValue);
        Page<Post> posts = categories.isEmpty()
                ? postRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : postRepository.findByUserIdAndCategoryInOrderByCreatedAtDesc(userId, categories, pageable);
        return PageResponse.of(posts, postAssembler.toSummaries(posts.getContent()));
    }

    public PageResponse<PostSummaryResponse> getLikedPosts(UUID userId, String categoryValue, int page, int size) {
        Pageable pageable = pageable(page, size);
        List<Category> categories = parseCategories(categoryValue);
        Page<Post> posts = categories.isEmpty()
                ? postRepository.findLikedByUserId(userId, pageable)
                : postRepository.findLikedByUserIdAndCategoryIn(userId, categories, pageable);
        return PageResponse.of(posts, withActedAt(posts.getContent(), likedAtByPostId(userId, posts.getContent())));
    }

    /**
     * 내가 가져온(포크한) 여행 — 포크 기록을 기준으로 페이징하고 게시글을 배치로 채운다.
     * 원본이 삭제된 포크는 @SQLRestriction으로 게시글 조회에서 빠지므로 목록에서 제외한다.
     */
    public PageResponse<PostSummaryResponse> getForkedPosts(UUID userId, int page, int size) {
        Page<FeedFork> forks = feedForkRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable(page, size));

        Map<Long, LocalDateTime> forkedAtByPostId = forks.getContent().stream()
                .collect(Collectors.toMap(FeedFork::getPostId, FeedFork::getCreatedAt));
        Map<Long, Post> postsById = postRepository.findAllById(forkedAtByPostId.keySet()).stream()
                .collect(Collectors.toMap(Post::getPostId, post -> post));

        List<Post> posts = forks.getContent().stream()
                .map(fork -> postsById.get(fork.getPostId()))
                .filter(Objects::nonNull)
                .toList();

        return PageResponse.of(forks, withActedAt(posts, forkedAtByPostId));
    }

    public PageResponse<CommentResponse> getMyComments(UUID userId, int page, int size) {
        Page<Comment> comments = commentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable(page, size));

        String freshNickname = userClient.getNickname(userId).orElse(null);
        int level = userStatsRepository.findById(userId).map(UserStats::getLevel).orElse(1);

        // 목록에서 원문 제목 노출 + 원문으로 이동해야 하므로 한 번에 조회한다 (N+1 방지)
        Map<Long, Post> postsById = postRepository.findAllById(
                        comments.getContent().stream().map(Comment::getPostId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Post::getPostId, post -> post));

        return PageResponse.of(comments, comments.getContent().stream()
                .map(comment -> CommentResponse.of(
                        comment, freshNickname, level, postsById.get(comment.getPostId())))
                .toList());
    }

    public MyStatsResponse getMyStats(UUID userId) {
        return MyStatsResponse.of(userId, userStatsRepository.findById(userId).orElse(null));
    }

    private List<PostSummaryResponse> withActedAt(List<Post> posts, Map<Long, LocalDateTime> actedAtByPostId) {
        return postAssembler.toSummaries(posts).stream()
                .map(summary -> summary.withActedAt(actedAtByPostId.get(summary.id())))
                .toList();
    }

    // "free,qna" 처럼 쉼표로 구분된 카테고리 목록을 파싱한다 (빈 값이면 전체)
    private List<Category> parseCategories(String categoryValue) {
        if (categoryValue == null || categoryValue.isBlank()) {
            return List.of();
        }
        return Arrays.stream(categoryValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Category::from)
                .distinct()
                .toList();
    }

    private Map<Long, LocalDateTime> likedAtByPostId(UUID userId, List<Post> posts) {
        if (posts.isEmpty()) {
            return Map.of();
        }
        return reactionRepository.findByUserIdAndPostIdIn(userId, posts.stream().map(Post::getPostId).toList()).stream()
                .collect(Collectors.toMap(Reaction::getPostId, Reaction::getCreatedAt));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
