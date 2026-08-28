package com.planmate.community.domain.comment.service;

import build.buf.gen.planmate.notification.v1.NotificationType;
import com.planmate.community.common.client.AuthorProfile;
import com.planmate.community.common.client.UserClient;
import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.common.notification.CommunityNotificationFactory;
import com.planmate.community.common.notification.NotificationOutboxWriter;
import com.planmate.community.domain.comment.dto.*;
import com.planmate.community.domain.comment.entity.FeedComment;
import com.planmate.community.domain.comment.repository.FeedCommentRepository;
import com.planmate.community.domain.post.entity.FeedPost;
import com.planmate.community.domain.post.repository.FeedPostRepository;
import com.planmate.community.domain.stats.entity.UserStats;
import com.planmate.community.domain.stats.repository.UserStatsRepository;
import com.planmate.community.domain.stats.service.UserStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedCommentService {
    private final FeedCommentRepository commentRepository;
    private final FeedPostRepository postRepository;
    private final UserStatsRepository userStatsRepository;
    private final UserClient userClient;
    private final UserStatsService userStatsService;
    private final CommunityNotificationFactory notificationFactory;
    private final NotificationOutboxWriter notificationOutbox;

    @Transactional
    public CommentResponse createComment(UUID userId, Long feedId, CommentCreateRequest request) {
        FeedPost post = findPost(feedId);
        FeedComment parent = validateParent(feedId, request.parentId());
        AuthorProfile author = userClient.getAuthor(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.INTERNAL_SERVER_ERROR, "사용자 정보를 가져올 수 없습니다."));
        FeedComment saved = commentRepository.save(FeedComment.create(feedId, userId, author.nickname(), request.content(), request.parentId()));
        postRepository.addCommentCount(feedId, 1);
        userStatsService.recordCommentCreated(userId);
        UUID recipient = parent == null ? post.getUserId() : parent.getUserId();
        if (!recipient.equals(userId)) {
            NotificationType type = parent == null ? NotificationType.NOTIFICATION_TYPE_COMMUNITY_POST_COMMENTED
                    : NotificationType.NOTIFICATION_TYPE_COMMUNITY_COMMENT_REPLIED;
            notificationOutbox.publish(notificationFactory.create(recipient, userId, author.nickname(), type,
                    "FEED", feedId.toString(), post.getTitle(), "FEED_POST",
                    Map.of("feedId", feedId.toString(), "commentId", saved.getCommentId().toString())));
        }
        return CommentResponse.of(saved, author, level(userId));
    }

    public PageResponse<CommentResponse> getComments(Long feedId, int page, int size) {
        findPost(feedId);
        Page<FeedComment> comments = commentRepository.findByPostIdOrderByCreatedAtAsc(feedId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        List<UUID> ids = comments.getContent().stream().map(FeedComment::getUserId).distinct().toList();
        Map<UUID, AuthorProfile> authors = userClient.getAuthors(ids);
        Map<UUID, Integer> levels = userStatsRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(UserStats::getUserId, UserStats::getLevel));
        return PageResponse.of(comments, comments.getContent().stream()
                .map(c -> CommentResponse.of(c, authors.get(c.getUserId()), levels.getOrDefault(c.getUserId(), 1))).toList());
    }

    @Transactional
    public CommentResponse updateComment(UUID userId, Long id, CommentUpdateRequest request) {
        FeedComment comment = findComment(id);
        if (!comment.isAuthor(userId)) throw new CommunityException(ErrorCode.COMMENT_ACCESS_DENIED);
        comment.updateContent(request.content());
        return CommentResponse.of(comment, userClient.getAuthor(comment.getUserId()).orElse(null), level(comment.getUserId()));
    }

    @Transactional
    public void deleteComment(UUID userId, boolean isAdmin, Long id) {
        FeedComment comment = findComment(id);
        if (!isAdmin && !comment.isAuthor(userId)) throw new CommunityException(ErrorCode.COMMENT_ACCESS_DENIED);
        List<FeedComment> replies = comment.getParentId() == null ? commentRepository.findByParentId(id) : List.of();
        comment.softDelete();
        replies.forEach(FeedComment::softDelete);
        postRepository.addCommentCount(comment.getPostId(), -(1 + replies.size()));
        userStatsService.recordCommentDeleted(comment.getUserId());
        replies.forEach(r -> userStatsService.recordCommentDeleted(r.getUserId()));
    }

    private FeedPost findPost(Long id) { return postRepository.findById(id).orElseThrow(() -> new CommunityException(ErrorCode.POST_NOT_FOUND)); }
    private FeedComment findComment(Long id) { return commentRepository.findById(id).orElseThrow(() -> new CommunityException(ErrorCode.COMMENT_NOT_FOUND)); }
    private int level(UUID id) { return userStatsRepository.findById(id).map(UserStats::getLevel).orElse(1); }
    private FeedComment validateParent(Long feedId, Long parentId) {
        if (parentId == null) return null;
        FeedComment parent = findComment(parentId);
        if (!parent.getPostId().equals(feedId)) throw new CommunityException(ErrorCode.INVALID_INPUT, "부모 댓글이 다른 피드에 속합니다.");
        if (parent.getParentId() != null) throw new CommunityException(ErrorCode.COMMENT_REPLY_DEPTH_EXCEEDED);
        return parent;
    }
}
