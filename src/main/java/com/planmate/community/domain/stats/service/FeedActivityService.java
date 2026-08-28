package com.planmate.community.domain.stats.service;

import com.planmate.community.common.access.ProfileAccessValidator;
import com.planmate.community.common.client.AuthorProfile;
import com.planmate.community.common.client.UserClient;
import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.domain.comment.dto.CommentResponse;
import com.planmate.community.domain.comment.entity.FeedComment;
import com.planmate.community.domain.comment.repository.FeedCommentRepository;
import com.planmate.community.domain.post.dto.PostSummaryResponse;
import com.planmate.community.domain.post.entity.FeedPost;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.repository.FeedPostRepository;
import com.planmate.community.domain.post.service.PostAssembler;
import com.planmate.community.domain.reaction.entity.FeedReaction;
import com.planmate.community.domain.reaction.repository.FeedReactionRepository;
import com.planmate.community.domain.stats.entity.UserStats;
import com.planmate.community.domain.stats.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedActivityService {
    private final FeedPostRepository postRepository;
    private final FeedCommentRepository commentRepository;
    private final FeedReactionRepository reactionRepository;
    private final UserStatsRepository statsRepository;
    private final UserClient userClient;
    private final ProfileAccessValidator profileAccessValidator;
    private final PostAssembler assembler;

    public PageResponse<PostSummaryResponse> posts(UUID userId, UUID viewerId, int page, int size) {
        profileAccessValidator.validateVisible(userId, viewerId);
        Page<FeedPost> posts = postRepository.findByUserId(userId, pageable(page, size));
        return PageResponse.of(posts, assembler.toSummaries(new ArrayList<>(posts.getContent())));
    }

    public PageResponse<PostSummaryResponse> liked(UUID userId, int page, int size) {
        Page<FeedPost> posts = postRepository.findLikedByUserId(userId, pageable(page, size));
        List<Long> ids = posts.getContent().stream().map(Post::getPostId).toList();
        Map<Long, LocalDateTime> acted = ids.isEmpty() ? Map.of() : reactionRepository.findByUserIdAndPostIdIn(userId, ids)
                .stream().collect(Collectors.toMap(FeedReaction::getPostId, FeedReaction::getCreatedAt));
        List<PostSummaryResponse> items = assembler.toSummaries(new ArrayList<>(posts.getContent())).stream()
                .map(p -> p.withActedAt(acted.get(p.id()))).toList();
        return PageResponse.of(posts, items);
    }

    public PageResponse<CommentResponse> comments(UUID userId, int page, int size) {
        return comments(userId, userId, page, size);
    }

    /** 다른 사용자의 프로필에서 볼 때 — 커뮤니티 쪽과 같은 공개 범위 게이트를 탄다 */
    public PageResponse<CommentResponse> comments(UUID userId, UUID viewerId, int page, int size) {
        profileAccessValidator.validateVisible(userId, viewerId);
        Page<FeedComment> comments = commentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable(page, size));
        AuthorProfile author = userClient.getAuthor(userId).orElse(null);
        int level = statsRepository.findById(userId).map(UserStats::getLevel).orElse(1);
        Map<Long, FeedPost> posts = postRepository.findAllById(comments.getContent().stream().map(FeedComment::getPostId).toList())
                .stream().collect(Collectors.toMap(FeedPost::getPostId, p -> p));
        return PageResponse.of(comments, comments.getContent().stream()
                .map(c -> CommentResponse.of(c, author, level, posts.get(c.getPostId()))).toList());
    }

    private Pageable pageable(int page, int size) { return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)); }
}
