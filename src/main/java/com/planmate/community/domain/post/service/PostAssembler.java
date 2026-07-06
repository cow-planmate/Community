package com.planmate.community.domain.post.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.community.common.client.UserClient;
import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.participant.repository.MateParticipantRepository;
import com.planmate.community.domain.post.dto.PostDetailResponse;
import com.planmate.community.domain.post.dto.PostSummaryResponse;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.stats.entity.UserStats;
import com.planmate.community.domain.stats.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 게시글 응답 조립 — 최신 닉네임(내부 API 캐시), 레벨, 메이트 참여자 수를 배치로 채운다.
 */
@Component
@RequiredArgsConstructor
public class PostAssembler {

    private final UserClient userClient;
    private final UserStatsRepository userStatsRepository;
    private final MateParticipantRepository mateParticipantRepository;
    private final ObjectMapper objectMapper;

    public List<PostSummaryResponse> toSummaries(List<Post> posts) {
        List<UUID> userIds = posts.stream().map(Post::getUserId).distinct().toList();
        Map<UUID, String> freshNicknames = userClient.getNicknames(userIds);
        Map<UUID, Integer> levels = findLevels(userIds);
        Map<Long, Integer> participantCounts = findParticipantCounts(posts);

        return posts.stream()
                .map(post -> PostSummaryResponse.of(
                        post,
                        freshNicknames.get(post.getUserId()),
                        levels.getOrDefault(post.getUserId(), 1),
                        participantsFor(post, participantCounts),
                        readTags(post)))
                .toList();
    }

    public PostDetailResponse toDetail(Post post, String myReaction, Boolean myFork) {
        String freshNickname = userClient.getNickname(post.getUserId()).orElse(null);
        int level = userStatsRepository.findById(post.getUserId()).map(UserStats::getLevel).orElse(1);
        Integer participants = post.getCategory() == Category.MATE
                ? (int) mateParticipantRepository.countByPostId(post.getPostId())
                : null;
        return PostDetailResponse.of(post, freshNickname, level, readContent(post.getContent()), myReaction, participants,
                readTags(post), post.getItinerary() != null ? readContent(post.getItinerary()) : null, myFork);
    }

    public JsonNode readContent(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException e) {
            throw new CommunityException(ErrorCode.INTERNAL_SERVER_ERROR, "내용을 읽을 수 없습니다.");
        }
    }

    private List<String> readTags(Post post) {
        if (post.getTags() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(post.getTags(), new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new CommunityException(ErrorCode.INTERNAL_SERVER_ERROR, "태그를 읽을 수 없습니다.");
        }
    }

    private Map<UUID, Integer> findLevels(List<UUID> userIds) {
        return userStatsRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserStats::getUserId, UserStats::getLevel));
    }

    private Map<Long, Integer> findParticipantCounts(List<Post> posts) {
        List<Long> matePostIds = posts.stream()
                .filter(post -> post.getCategory() == Category.MATE)
                .map(Post::getPostId)
                .toList();
        if (matePostIds.isEmpty()) {
            return Map.of();
        }
        return mateParticipantRepository.countByPostIds(matePostIds).stream()
                .collect(Collectors.toMap(
                        MateParticipantRepository.ParticipantCount::getPostId,
                        count -> (int) count.getParticipantCount()));
    }

    private Integer participantsFor(Post post, Map<Long, Integer> participantCounts) {
        return post.getCategory() == Category.MATE
                ? participantCounts.getOrDefault(post.getPostId(), 0)
                : null;
    }
}
