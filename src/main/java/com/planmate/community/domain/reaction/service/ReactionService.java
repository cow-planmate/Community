package com.planmate.community.domain.reaction.service;

import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.repository.PostRepository;
import com.planmate.community.domain.reaction.dto.ReactionResponse;
import com.planmate.community.domain.reaction.entity.Reaction;
import com.planmate.community.domain.reaction.enums.ReactionType;
import com.planmate.community.domain.reaction.repository.ReactionRepository;
import com.planmate.community.domain.stats.service.UserStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final PostRepository postRepository;
    private final UserStatsService userStatsService;

    /**
     * 반응 등록/토글/전환.
     * - 반응 없음 → 등록
     * - 같은 타입 → 해제(토글)
     * - 다른 타입 → 전환 (기존 카운터 감소 + 새 카운터 증가)
     */
    @Transactional
    public ReactionResponse react(UUID userId, Long postId, String typeValue) {
        ReactionType type = ReactionType.from(typeValue);
        Post post = findPost(postId);

        Optional<Reaction> existing = reactionRepository.findByPostIdAndUserId(postId, userId);
        String myReaction;
        int likeDelta;

        if (existing.isEmpty()) {
            reactionRepository.save(Reaction.builder()
                    .postId(postId)
                    .userId(userId)
                    .type(type)
                    .build());
            addCount(postId, type, 1);
            myReaction = type.toLowerValue();
            likeDelta = likeDelta(type, 1);
        } else if (existing.get().getType() == type) {
            reactionRepository.delete(existing.get());
            addCount(postId, type, -1);
            myReaction = null;
            likeDelta = likeDelta(type, -1);
        } else {
            ReactionType previous = existing.get().getType();
            existing.get().changeType(type);
            addCount(postId, previous, -1);
            addCount(postId, type, 1);
            myReaction = type.toLowerValue();
            likeDelta = likeDelta(previous, -1) + likeDelta(type, 1);
        }

        recordReceivedLikes(post.getUserId(), likeDelta);
        return buildResponse(postId, myReaction);
    }

    @Transactional
    public ReactionResponse cancelReaction(UUID userId, Long postId) {
        Post post = findPost(postId);

        reactionRepository.findByPostIdAndUserId(postId, userId).ifPresent(reaction -> {
            reactionRepository.delete(reaction);
            addCount(postId, reaction.getType(), -1);
            recordReceivedLikes(post.getUserId(), likeDelta(reaction.getType(), -1));
        });

        return buildResponse(postId, null);
    }

    private Post findPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.POST_NOT_FOUND));
    }

    // 작성자의 "받은 좋아요"는 like 에만 반응한다 (dislike 는 집계 대상이 아니다)
    private static int likeDelta(ReactionType type, int delta) {
        return type == ReactionType.LIKE ? delta : 0;
    }

    private void recordReceivedLikes(UUID authorId, int delta) {
        if (delta != 0) {
            userStatsService.recordLikeReceived(authorId, delta);
        }
    }

    private void addCount(Long postId, ReactionType type, int delta) {
        if (type == ReactionType.LIKE) {
            postRepository.addLikeCount(postId, delta);
        } else {
            postRepository.addDislikeCount(postId, delta);
        }
    }

    private ReactionResponse buildResponse(Long postId, String myReaction) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new CommunityException(ErrorCode.POST_NOT_FOUND));
        return new ReactionResponse(post.getLikeCount(), post.getDislikeCount(), myReaction);
    }
}
