package com.planmate.community.domain.fork.service;

import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.fork.dto.ForkResponse;
import com.planmate.community.domain.fork.repository.FeedForkRepository;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedForkService {

    private final FeedForkRepository feedForkRepository;
    private final PostRepository postRepository;

    /**
     * 피드 일정 가져가기(포크) — 횟수 제한 없음.
     * 가져갈 때마다 사용자의 여행에 새 플랜이 하나씩 생기므로 fork_count도 매번 증가한다.
     * 반면 "내가 가져온 여행" 목록은 글이 중복 노출되면 안 되므로 기록은 (post, user)당 1행으로 UPSERT한다.
     */
    @Transactional
    public ForkResponse fork(UUID userId, Long postId) {
        findFeedPost(postId);

        feedForkRepository.upsertFork(postId, userId, LocalDateTime.now());
        postRepository.addForkCount(postId, 1);

        return buildResponse(postId);
    }

    private Post findFeedPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.POST_NOT_FOUND));
        if (post.getCategory() != Category.FEED) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "피드 게시글이 아닙니다.");
        }
        return post;
    }

    // addForkCount(@Modifying clearAutomatically)로 영속성 컨텍스트가 비워지므로 재조회로 최신 카운트를 반환한다 (ReactionService 관례)
    private ForkResponse buildResponse(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.POST_NOT_FOUND));
        return ForkResponse.of(post, true);
    }
}
