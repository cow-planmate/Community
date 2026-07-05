package com.planmate.community.domain.reaction.service;

import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.post.repository.PostRepository;
import com.planmate.community.domain.reaction.entity.Reaction;
import com.planmate.community.domain.reaction.enums.ReactionType;
import com.planmate.community.domain.reaction.repository.ReactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactionServiceTest {

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private ReactionService reactionService;

    private final UUID userId = UUID.randomUUID();

    private Post post() {
        return Post.builder()
                .category(Category.FREE)
                .userId(UUID.randomUUID())
                .authorNickname("작성자")
                .title("제목")
                .content("{}")
                .contentText("본문")
                .build();
    }

    @Test
    @DisplayName("반응이 없으면 등록하고 카운터를 증가시킨다")
    void reactNew() {
        when(postRepository.existsById(1L)).thenReturn(true);
        when(reactionRepository.findByPostIdAndUserId(1L, userId)).thenReturn(Optional.empty());
        when(postRepository.findById(1L)).thenReturn(Optional.of(post()));

        var response = reactionService.react(userId, 1L, "like");

        verify(reactionRepository).save(any(Reaction.class));
        verify(postRepository).addLikeCount(1L, 1);
        assertThat(response.myReaction()).isEqualTo("like");
    }

    @Test
    @DisplayName("같은 타입을 다시 보내면 해제(토글)된다")
    void reactToggleOff() {
        Reaction existing = Reaction.builder().postId(1L).userId(userId).type(ReactionType.LIKE).build();
        when(postRepository.existsById(1L)).thenReturn(true);
        when(reactionRepository.findByPostIdAndUserId(1L, userId)).thenReturn(Optional.of(existing));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post()));

        var response = reactionService.react(userId, 1L, "like");

        verify(reactionRepository).delete(existing);
        verify(postRepository).addLikeCount(1L, -1);
        assertThat(response.myReaction()).isNull();
    }

    @Test
    @DisplayName("다른 타입이면 전환되어 양쪽 카운터가 조정된다")
    void reactSwitch() {
        Reaction existing = Reaction.builder().postId(1L).userId(userId).type(ReactionType.LIKE).build();
        when(postRepository.existsById(1L)).thenReturn(true);
        when(reactionRepository.findByPostIdAndUserId(1L, userId)).thenReturn(Optional.of(existing));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post()));

        var response = reactionService.react(userId, 1L, "dislike");

        verify(postRepository).addLikeCount(1L, -1);
        verify(postRepository).addDislikeCount(1L, 1);
        verify(reactionRepository, never()).save(any());
        assertThat(existing.getType()).isEqualTo(ReactionType.DISLIKE);
        assertThat(response.myReaction()).isEqualTo("dislike");
    }

    @Test
    @DisplayName("존재하지 않는 게시글에 반응하면 POST_NOT_FOUND 예외가 발생한다")
    void reactPostNotFound() {
        when(postRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> reactionService.react(userId, 99L, "like"))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.POST_NOT_FOUND));
    }

    @Test
    @DisplayName("잘못된 반응 타입은 INVALID_INPUT 예외가 발생한다")
    void reactInvalidType() {
        assertThatThrownBy(() -> reactionService.react(userId, 1L, "love"))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }
}
