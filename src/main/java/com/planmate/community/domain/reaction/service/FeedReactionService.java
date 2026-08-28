package com.planmate.community.domain.reaction.service;

import build.buf.gen.planmate.notification.v1.NotificationType;
import com.planmate.community.common.client.UserClient;
import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.common.notification.CommunityNotificationFactory;
import com.planmate.community.common.notification.NotificationOutboxWriter;
import com.planmate.community.domain.post.entity.FeedPost;
import com.planmate.community.domain.post.repository.FeedPostRepository;
import com.planmate.community.domain.reaction.dto.ReactionResponse;
import com.planmate.community.domain.reaction.entity.FeedReaction;
import com.planmate.community.domain.reaction.enums.ReactionType;
import com.planmate.community.domain.reaction.repository.FeedReactionRepository;
import com.planmate.community.domain.stats.service.UserStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedReactionService {
    private final FeedReactionRepository reactionRepository;
    private final FeedPostRepository postRepository;
    private final UserStatsService userStatsService;
    private final UserClient userClient;
    private final CommunityNotificationFactory notificationFactory;
    private final NotificationOutboxWriter notificationOutbox;

    @Transactional
    public ReactionResponse react(UUID userId, Long feedId, String value) {
        ReactionType type = ReactionType.from(value);
        FeedPost post = findPost(feedId);
        var existing = reactionRepository.findByPostIdAndUserId(feedId, userId);
        String mine;
        int likeDelta;
        boolean notify = false;
        if (existing.isEmpty()) {
            reactionRepository.save(FeedReaction.create(feedId, userId, type));
            addCount(feedId, type, 1); mine = type.toLowerValue(); likeDelta = likeDelta(type, 1); notify = type == ReactionType.LIKE;
        } else if (existing.get().getType() == type) {
            reactionRepository.delete(existing.get());
            addCount(feedId, type, -1); mine = null; likeDelta = likeDelta(type, -1);
        } else {
            ReactionType previous = existing.get().getType();
            existing.get().changeType(type);
            addCount(feedId, previous, -1); addCount(feedId, type, 1);
            mine = type.toLowerValue(); likeDelta = likeDelta(previous, -1) + likeDelta(type, 1); notify = type == ReactionType.LIKE;
        }
        if (likeDelta != 0) userStatsService.recordLikeReceived(post.getUserId(), likeDelta);
        if (notify && !post.getUserId().equals(userId)) {
            String actor = userClient.getAuthor(userId).map(p -> p.nickname()).orElse("누군가");
            notificationOutbox.publish(notificationFactory.create(post.getUserId(), userId, actor,
                    NotificationType.NOTIFICATION_TYPE_COMMUNITY_POST_LIKED, "FEED", feedId.toString(),
                    post.getTitle(), "FEED_POST", Map.of("feedId", feedId.toString())));
        }
        return response(feedId, mine);
    }

    @Transactional
    public ReactionResponse cancel(UUID userId, Long feedId) {
        FeedPost post = findPost(feedId);
        reactionRepository.findByPostIdAndUserId(feedId, userId).ifPresent(r -> {
            reactionRepository.delete(r); addCount(feedId, r.getType(), -1);
            int delta = likeDelta(r.getType(), -1);
            if (delta != 0) userStatsService.recordLikeReceived(post.getUserId(), delta);
        });
        return response(feedId, null);
    }

    private FeedPost findPost(Long id) { return postRepository.findById(id).orElseThrow(() -> new CommunityException(ErrorCode.POST_NOT_FOUND)); }
    private void addCount(Long id, ReactionType type, int delta) {
        if (type == ReactionType.LIKE) postRepository.addLikeCount(id, delta); else postRepository.addDislikeCount(id, delta);
    }
    private int likeDelta(ReactionType type, int delta) { return type == ReactionType.LIKE ? delta : 0; }
    private ReactionResponse response(Long id, String mine) {
        FeedPost p = findPost(id); return new ReactionResponse(p.getLikeCount(), p.getDislikeCount(), mine);
    }
}
