package com.planmate.community.domain.comment.service;

import com.planmate.community.common.client.UserClient;
import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.comment.dto.CommentCreateRequest;
import com.planmate.community.domain.comment.dto.CommentUpdateRequest;
import com.planmate.community.domain.comment.entity.Comment;
import com.planmate.community.domain.comment.repository.CommentRepository;
import com.planmate.community.domain.post.repository.PostRepository;
import com.planmate.community.domain.stats.repository.UserStatsRepository;
import com.planmate.community.domain.stats.service.UserStatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserStatsRepository userStatsRepository;

    @Mock
    private UserClient userClient;

    @Mock
    private UserStatsService userStatsService;

    @InjectMocks
    private CommentService commentService;

    private final UUID userId = UUID.randomUUID();

    private Comment comment(UUID authorId) {
        Comment comment = Comment.builder()
                .postId(1L)
                .userId(authorId)
                .authorNickname("작성자")
                .content("원본 댓글")
                .build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);
        return comment;
    }

    @Test
    @DisplayName("댓글 작성 시 닉네임 스냅샷 저장과 댓글 수 증가가 수행된다")
    void createComment() {
        when(postRepository.existsById(1L)).thenReturn(true);
        when(userClient.getNickname(userId)).thenReturn(Optional.of("댓글러"));
        when(userStatsRepository.findById(userId)).thenReturn(Optional.empty());
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 10L);
            return saved;
        });

        var response = commentService.createComment(userId, 1L, new CommentCreateRequest("첫 댓글", null));

        verify(postRepository).addCommentCount(1L, 1);
        verify(userStatsService).recordCommentCreated(userId);
        assertThat(response.author()).isEqualTo("댓글러");
        assertThat(response.content()).isEqualTo("첫 댓글");
        assertThat(response.level()).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 게시글에는 댓글을 달 수 없다")
    void createCommentPostNotFound() {
        when(postRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.createComment(userId, 99L, new CommentCreateRequest("댓글", null)))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.POST_NOT_FOUND));
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("작성자가 아니면 댓글 수정이 거부된다")
    void updateCommentByNonAuthor() {
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment(userId)));

        assertThatThrownBy(() -> commentService.updateComment(UUID.randomUUID(), 10L, new CommentUpdateRequest("수정")))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.COMMENT_ACCESS_DENIED));
    }

    @Test
    @DisplayName("댓글 삭제 시 soft delete와 댓글 수 감소가 수행된다")
    void deleteComment() {
        Comment target = comment(userId);
        when(commentRepository.findById(10L)).thenReturn(Optional.of(target));

        commentService.deleteComment(userId, false, 10L);

        assertThat(target.isDeleted()).isTrue();
        verify(postRepository).addCommentCount(1L, -1);
        verify(userStatsService).recordCommentDeleted(userId);
    }

    private Comment reply(UUID authorId, Long parentId, Long commentId) {
        Comment comment = Comment.builder()
                .postId(1L)
                .userId(authorId)
                .authorNickname("답글러")
                .content("대댓글")
                .parentId(parentId)
                .build();
        ReflectionTestUtils.setField(comment, "commentId", commentId);
        return comment;
    }

    @Test
    @DisplayName("대댓글 작성 시 parentId가 저장된다")
    void createReply() {
        Comment parent = comment(UUID.randomUUID());
        when(postRepository.existsById(1L)).thenReturn(true);
        when(commentRepository.findById(10L)).thenReturn(Optional.of(parent));
        when(userClient.getNickname(userId)).thenReturn(Optional.of("답글러"));
        when(userStatsRepository.findById(userId)).thenReturn(Optional.empty());
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 11L);
            return saved;
        });

        var response = commentService.createComment(userId, 1L, new CommentCreateRequest("감사합니다", 10L));

        assertThat(response.parentId()).isEqualTo(10L);
        verify(postRepository).addCommentCount(1L, 1);
    }

    @Test
    @DisplayName("대댓글에는 답글을 달 수 없다 (깊이 1 제한)")
    void createReplyToReplyRejected() {
        Comment parentReply = reply(UUID.randomUUID(), 10L, 11L);
        when(postRepository.existsById(1L)).thenReturn(true);
        when(commentRepository.findById(11L)).thenReturn(Optional.of(parentReply));

        assertThatThrownBy(() -> commentService.createComment(userId, 1L, new CommentCreateRequest("깊이2", 11L)))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.COMMENT_REPLY_DEPTH_EXCEEDED));
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 게시글의 댓글을 부모로 지정할 수 없다")
    void createReplyCrossPostRejected() {
        Comment parent = comment(UUID.randomUUID()); // postId=1
        when(postRepository.existsById(2L)).thenReturn(true);
        when(commentRepository.findById(10L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> commentService.createComment(userId, 2L, new CommentCreateRequest("교차", 10L)))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는(또는 삭제된) 부모 댓글에는 답글을 달 수 없다")
    void createReplyParentNotFound() {
        when(postRepository.existsById(1L)).thenReturn(true);
        when(commentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.createComment(userId, 1L, new CommentCreateRequest("고아", 99L)))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.COMMENT_NOT_FOUND));
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("최상위 댓글 삭제 시 대댓글까지 연쇄 soft delete되고 카운트·통계가 함께 차감된다")
    void deleteParentCascadesReplies() {
        UUID replyAuthor1 = UUID.randomUUID();
        UUID replyAuthor2 = UUID.randomUUID();
        Comment parent = comment(userId);
        Comment reply1 = reply(replyAuthor1, 10L, 11L);
        Comment reply2 = reply(replyAuthor2, 10L, 12L);
        when(commentRepository.findById(10L)).thenReturn(Optional.of(parent));
        when(commentRepository.findByParentId(10L)).thenReturn(java.util.List.of(reply1, reply2));

        commentService.deleteComment(userId, false, 10L);

        assertThat(parent.isDeleted()).isTrue();
        assertThat(reply1.isDeleted()).isTrue();
        assertThat(reply2.isDeleted()).isTrue();
        verify(postRepository).addCommentCount(1L, -3);
        verify(userStatsService).recordCommentDeleted(userId);
        verify(userStatsService).recordCommentDeleted(replyAuthor1);
        verify(userStatsService).recordCommentDeleted(replyAuthor2);
    }

    @Test
    @DisplayName("대댓글 단독 삭제는 카운트를 1만 차감하고 연쇄 조회를 하지 않는다")
    void deleteReplyOnly() {
        Comment target = reply(userId, 10L, 11L);
        when(commentRepository.findById(11L)).thenReturn(Optional.of(target));

        commentService.deleteComment(userId, false, 11L);

        assertThat(target.isDeleted()).isTrue();
        verify(postRepository).addCommentCount(1L, -1);
        verify(commentRepository, never()).findByParentId(any());
        verify(userStatsService).recordCommentDeleted(userId);
    }
}
