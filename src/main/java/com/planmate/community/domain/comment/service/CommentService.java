package com.planmate.community.domain.comment.service;

import com.planmate.community.common.client.UserClient;
import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.comment.dto.CommentCreateRequest;
import com.planmate.community.domain.comment.dto.CommentResponse;
import com.planmate.community.domain.comment.dto.CommentUpdateRequest;
import com.planmate.community.domain.comment.entity.Comment;
import com.planmate.community.domain.comment.repository.CommentRepository;
import com.planmate.community.domain.post.repository.PostRepository;
import com.planmate.community.domain.stats.entity.UserStats;
import com.planmate.community.domain.stats.repository.UserStatsRepository;
import com.planmate.community.domain.stats.service.UserStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserStatsRepository userStatsRepository;
    private final UserClient userClient;
    private final UserStatsService userStatsService;

    @Transactional
    public CommentResponse createComment(UUID userId, Long postId, CommentCreateRequest request) {
        ensurePostExists(postId);
        validateParent(postId, request.parentId());

        String nickname = userClient.getNickname(userId)
                .orElseThrow(() -> new CommunityException(ErrorCode.INTERNAL_SERVER_ERROR, "사용자 정보를 가져올 수 없습니다."));

        Comment comment = Comment.builder()
                .postId(postId)
                .userId(userId)
                .authorNickname(nickname)
                .content(request.content())
                .parentId(request.parentId())
                .build();

        Comment saved = commentRepository.save(comment);
        postRepository.addCommentCount(postId, 1);
        userStatsService.recordCommentCreated(userId);
        return CommentResponse.of(saved, nickname, findLevel(userId));
    }

    public PageResponse<CommentResponse> getComments(Long postId, int page, int size) {
        ensurePostExists(postId);

        Page<Comment> comments = commentRepository.findByPostIdOrderByCreatedAtAsc(
                postId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));

        List<UUID> userIds = comments.getContent().stream().map(Comment::getUserId).distinct().toList();
        Map<UUID, String> freshNicknames = userClient.getNicknames(userIds);
        Map<UUID, Integer> levels = userStatsRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserStats::getUserId, UserStats::getLevel));

        List<CommentResponse> items = comments.getContent().stream()
                .map(comment -> CommentResponse.of(
                        comment,
                        freshNicknames.get(comment.getUserId()),
                        levels.getOrDefault(comment.getUserId(), 1)))
                .toList();

        return PageResponse.of(comments, items);
    }

    @Transactional
    public CommentResponse updateComment(UUID userId, Long commentId, CommentUpdateRequest request) {
        Comment comment = findComment(commentId);
        if (!comment.isAuthor(userId)) {
            throw new CommunityException(ErrorCode.COMMENT_ACCESS_DENIED);
        }
        comment.updateContent(request.content());

        String freshNickname = userClient.getNickname(comment.getUserId()).orElse(null);
        return CommentResponse.of(comment, freshNickname, findLevel(comment.getUserId()));
    }

    @Transactional
    public void deleteComment(UUID userId, boolean isAdmin, Long commentId) {
        Comment comment = findComment(commentId);
        if (!isAdmin && !comment.isAuthor(userId)) {
            throw new CommunityException(ErrorCode.COMMENT_ACCESS_DENIED);
        }

        // 최상위 댓글이면 살아있는 대댓글도 연쇄 soft-delete (삭제행은 @SQLRestriction으로 숨겨져 대댓글이 고아 노출되는 것을 방지)
        List<Comment> replies = comment.getParentId() == null
                ? commentRepository.findByParentId(comment.getCommentId())
                : List.of();

        comment.softDelete();
        replies.forEach(Comment::softDelete);

        postRepository.addCommentCount(comment.getPostId(), -(1 + replies.size()));
        userStatsService.recordCommentDeleted(comment.getUserId());
        replies.forEach(reply -> userStatsService.recordCommentDeleted(reply.getUserId()));
    }

    private Comment findComment(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new CommunityException(ErrorCode.COMMENT_NOT_FOUND));
    }

    // 대댓글 생성 검증 — 부모 존재(soft-delete 제외), 같은 게시글, 깊이 1 제한
    private void validateParent(Long postId, Long parentId) {
        if (parentId == null) {
            return;
        }
        Comment parent = findComment(parentId);
        if (!parent.getPostId().equals(postId)) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "부모 댓글이 다른 게시글에 속합니다.");
        }
        if (parent.getParentId() != null) {
            throw new CommunityException(ErrorCode.COMMENT_REPLY_DEPTH_EXCEEDED);
        }
    }

    private void ensurePostExists(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new CommunityException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private int findLevel(UUID userId) {
        return userStatsRepository.findById(userId).map(UserStats::getLevel).orElse(1);
    }
}
