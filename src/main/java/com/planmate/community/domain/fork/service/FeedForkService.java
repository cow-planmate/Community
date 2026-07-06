package com.planmate.community.domain.fork.service;

import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.fork.dto.ForkResponse;
import com.planmate.community.domain.fork.entity.FeedFork;
import com.planmate.community.domain.fork.repository.FeedForkRepository;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedForkService {

    private final FeedForkRepository feedForkRepository;
    private final PostRepository postRepository;

    /**
     * 피드 일정 가져가기(포크) — 사용자당 게시글별 1회.
     * 동시 요청 레이스는 UNIQUE 제약이 백스톱하며, 위반이 커밋 시점이 아닌
     * try 안에서 감지되도록 반드시 saveAndFlush를 사용한다.
     */
    @Transactional
    public ForkResponse fork(UUID userId, Long postId) {
        findFeedPost(postId);

        if (feedForkRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new CommunityException(ErrorCode.FEED_ALREADY_FORKED);
        }

        try {
            feedForkRepository.saveAndFlush(FeedFork.builder()
                    .postId(postId)
                    .userId(userId)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new CommunityException(ErrorCode.FEED_ALREADY_FORKED);
        }

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
