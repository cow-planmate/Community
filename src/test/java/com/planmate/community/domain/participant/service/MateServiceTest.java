package com.planmate.community.domain.participant.service;

import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.participant.entity.MateParticipant;
import com.planmate.community.domain.participant.repository.MateParticipantRepository;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.post.enums.MateStatus;
import com.planmate.community.domain.post.repository.PostRepository;
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
class MateServiceTest {

    @Mock
    private MateParticipantRepository mateParticipantRepository;

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private MateService mateService;

    private final UUID userId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();

    private Post matePost(Integer maxParticipants, MateStatus status) {
        Post post = Post.builder()
                .category(Category.MATE)
                .userId(authorId)
                .authorNickname("모집자")
                .title("동행 구해요")
                .content("{}")
                .contentText("본문")
                .region("제주")
                .maxParticipants(maxParticipants)
                .status(status)
                .build();
        ReflectionTestUtils.setField(post, "postId", 1L);
        return post;
    }

    @Test
    @DisplayName("참여 성공 시 참여자가 저장되고, 정원이 차면 자동 마감된다")
    void joinAndAutoClose() {
        Post post = matePost(2, MateStatus.RECRUITING);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(mateParticipantRepository.findByPostIdAndUserId(1L, userId)).thenReturn(Optional.empty());
        when(mateParticipantRepository.countByPostId(1L)).thenReturn(1L);

        var response = mateService.join(userId, 1L);

        verify(mateParticipantRepository).save(any(MateParticipant.class));
        assertThat(post.getStatus()).isEqualTo(MateStatus.CLOSED);
        assertThat(response.participants()).isEqualTo(2);
        assertThat(response.status()).isEqualTo("closed");
    }

    @Test
    @DisplayName("이미 참여한 모집에는 다시 참여할 수 없다")
    void joinTwice() {
        Post post = matePost(4, MateStatus.RECRUITING);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(mateParticipantRepository.findByPostIdAndUserId(1L, userId))
                .thenReturn(Optional.of(MateParticipant.builder().postId(1L).userId(userId).build()));

        assertThatThrownBy(() -> mateService.join(userId, 1L))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.MATE_ALREADY_JOINED));
    }

    @Test
    @DisplayName("정원이 가득 찬 모집에는 참여할 수 없다")
    void joinFull() {
        Post post = matePost(2, MateStatus.RECRUITING);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(mateParticipantRepository.findByPostIdAndUserId(1L, userId)).thenReturn(Optional.empty());
        when(mateParticipantRepository.countByPostId(1L)).thenReturn(2L);

        assertThatThrownBy(() -> mateService.join(userId, 1L))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.MATE_FULL));
        verify(mateParticipantRepository, never()).save(any());
    }

    @Test
    @DisplayName("마감된 모집에는 참여할 수 없다")
    void joinClosed() {
        Post post = matePost(4, MateStatus.CLOSED);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> mateService.join(userId, 1L))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.MATE_CLOSED));
    }

    @Test
    @DisplayName("메이트 게시판이 아닌 글에는 참여할 수 없다")
    void joinNonMatePost() {
        Post post = Post.builder()
                .category(Category.FREE).userId(authorId).authorNickname("작성자")
                .title("자유글").content("{}").contentText("본문").build();
        ReflectionTestUtils.setField(post, "postId", 1L);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> mateService.join(userId, 1L))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("작성자가 아니면 모집 상태를 변경할 수 없다")
    void changeStatusByNonAuthor() {
        Post post = matePost(4, MateStatus.RECRUITING);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> mateService.changeStatus(userId, 1L, "closed"))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.POST_ACCESS_DENIED));
    }

    @Test
    @DisplayName("참여하지 않은 모집에서는 나갈 수 없다")
    void leaveWithoutJoin() {
        Post post = matePost(4, MateStatus.RECRUITING);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(mateParticipantRepository.findByPostIdAndUserId(1L, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mateService.leave(userId, 1L))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.MATE_NOT_JOINED));
    }
}
