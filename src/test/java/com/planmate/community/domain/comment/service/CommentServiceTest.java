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

        var response = commentService.createComment(userId, 1L, new CommentCreateRequest("첫 댓글"));

        verify(postRepository).addCommentCount(1L, 1);
        assertThat(response.author()).isEqualTo("댓글러");
        assertThat(response.content()).isEqualTo("첫 댓글");
        assertThat(response.level()).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 게시글에는 댓글을 달 수 없다")
    void createCommentPostNotFound() {
        when(postRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.createComment(userId, 99L, new CommentCreateRequest("댓글")))
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
    }
}
