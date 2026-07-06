package com.planmate.community.domain.fork.service;

import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.fork.entity.FeedFork;
import com.planmate.community.domain.fork.repository.FeedForkRepository;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.post.repository.PostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedForkServiceTest {

    @Mock
    private FeedForkRepository feedForkRepository;

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private FeedForkService feedForkService;

    private final UUID userId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();

    private Post feedPost(int forkCount) {
        Post post = Post.builder()
                .category(Category.FEED)
                .userId(authorId)
                .authorNickname("작성자")
                .title("서울 여행")
                .content("{}")
                .contentText("본문")
                .region("서울")
                .durationDays(3)
                .forkCount(forkCount)
                .build();
        ReflectionTestUtils.setField(post, "postId", 1L);
        return post;
    }

    @Test
    @DisplayName("가져가기 성공 시 포크가 저장되고 카운트가 증가하며 최신 카운트가 반환된다")
    void forkSuccess() {
        when(postRepository.findById(1L))
                .thenReturn(Optional.of(feedPost(0)))
                .thenReturn(Optional.of(feedPost(1)));
        when(feedForkRepository.existsByPostIdAndUserId(1L, userId)).thenReturn(false);

        var response = feedForkService.fork(userId, 1L);

        ArgumentCaptor<FeedFork> captor = ArgumentCaptor.forClass(FeedFork.class);
        verify(feedForkRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPostId()).isEqualTo(1L);
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        verify(postRepository).addForkCount(1L, 1);
        assertThat(response.forks()).isEqualTo(1);
        assertThat(response.myFork()).isTrue();
    }

    @Test
    @DisplayName("이미 가져간 일정은 다시 가져갈 수 없다")
    void forkTwice() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(feedPost(1)));
        when(feedForkRepository.existsByPostIdAndUserId(1L, userId)).thenReturn(true);

        assertThatThrownBy(() -> feedForkService.fork(userId, 1L))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.FEED_ALREADY_FORKED));
        verify(feedForkRepository, never()).saveAndFlush(any());
        verify(postRepository, never()).addForkCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("동시 요청 레이스로 UNIQUE 제약을 위반하면 409로 변환된다")
    void forkRace() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(feedPost(0)));
        when(feedForkRepository.existsByPostIdAndUserId(1L, userId)).thenReturn(false);
        when(feedForkRepository.saveAndFlush(any(FeedFork.class)))
                .thenThrow(new DataIntegrityViolationException("uq_community_feed_fork_post_user"));

        assertThatThrownBy(() -> feedForkService.fork(userId, 1L))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.FEED_ALREADY_FORKED));
        verify(postRepository, never()).addForkCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("피드 게시판이 아닌 글은 가져갈 수 없다")
    void forkNonFeedPost() {
        Post post = Post.builder()
                .category(Category.FREE).userId(authorId).authorNickname("작성자")
                .title("자유글").content("{}").contentText("본문").build();
        ReflectionTestUtils.setField(post, "postId", 1L);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> feedForkService.fork(userId, 1L))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        verify(feedForkRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("존재하지 않는 게시글은 가져갈 수 없다")
    void forkPostNotFound() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feedForkService.fork(userId, 99L))
                .isInstanceOf(CommunityException.class)
                .satisfies(e -> assertThat(((CommunityException) e).getErrorCode()).isEqualTo(ErrorCode.POST_NOT_FOUND));
    }
}
