package com.planmate.community.domain.reaction.service;

import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.repository.PostRepository;
import com.planmate.community.domain.reaction.dto.ReactionResponse;
import com.planmate.community.domain.reaction.entity.Reaction;
import com.planmate.community.domain.reaction.enums.ReactionType;
import com.planmate.community.domain.reaction.repository.ReactionRepository;
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

    /**
     * 반응 등록/토글/전환.
     * - 반응 없음 → 등록
     * - 같은 타입 → 해제(토글)
     * - 다른 타입 → 전환 (기존 카운터 감소 + 새 카운터 증가)
     */
    @Transactional
    public ReactionResponse react(UUID userId, Long postId, String typeValue) {
        ReactionType type = ReactionType.from(typeValue);
        ensurePostExists(postId);

        Optional<Reaction> existing = reactionRepository.findByPostIdAndUserId(postId, userId);
        String myReaction;

        if (existing.isEmpty()) {
            reactionRepository.save(Reaction.builder()
                    .postId(postId)
                    .userId(userId)
                    .type(type)
                    .build());
            addCount(postId, type, 1);
            myReaction = type.toLowerValue();
        } else if (existing.get().getType() == type) {
            reactionRepository.delete(existing.get());
            addCount(postId, type, -1);
            myReaction = null;
        } else {
            ReactionType previous = existing.get().getType();
            existing.get().changeType(type);
            addCount(postId, previous, -1);
            addCount(postId, type, 1);
            myReaction = type.toLowerValue();
        }

        return buildResponse(postId, myReaction);
    }

    @Transactional
    public ReactionResponse cancelReaction(UUID userId, Long postId) {
        ensurePostExists(postId);

        reactionRepository.findByPostIdAndUserId(postId, userId).ifPresent(reaction -> {
            reactionRepository.delete(reaction);
            addCount(postId, reaction.getType(), -1);
        });

        return buildResponse(postId, null);
    }

    private void ensurePostExists(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new CommunityException(ErrorCode.POST_NOT_FOUND);
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
