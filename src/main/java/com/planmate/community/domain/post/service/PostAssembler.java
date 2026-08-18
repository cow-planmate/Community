package com.planmate.community.domain.post.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.community.common.client.AuthorProfile;
import com.planmate.community.common.client.UserClient;
import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.participant.repository.MateParticipantRepository;
import com.planmate.community.domain.post.dto.PostDetailResponse;
import com.planmate.community.domain.post.dto.PostSummaryResponse;
import com.planmate.community.domain.post.dto.RecommendPlace;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;
import com.planmate.community.domain.stats.entity.UserStats;
import com.planmate.community.domain.stats.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 게시글 응답 조립 — 최신 작성자 정보(내부 API 캐시: 닉네임·프로필 아이콘), 레벨, 메이트 참여자 수를 배치로 채운다.
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
        Map<UUID, AuthorProfile> authors = userClient.getAuthors(userIds);
        Map<UUID, Integer> levels = findLevels(userIds);
        Map<Long, Integer> participantCounts = findParticipantCounts(posts);

        return posts.stream()
                .map(post -> PostSummaryResponse.of(
                        post,
                        authors.get(post.getUserId()),
                        levels.getOrDefault(post.getUserId(), 1),
                        participantsFor(post, participantCounts),
                        readTags(post),
                        readPlacePreview(post)))
                .toList();
    }

    public PostDetailResponse toDetail(Post post, String myReaction, Boolean myFork) {
        // 조회 실패(사용자 서비스 장애 등) 시 null — DTO가 게시글의 닉네임 스냅샷으로 fallback한다
        AuthorProfile author = userClient.getAuthor(post.getUserId()).orElse(null);
        int level = userStatsRepository.findById(post.getUserId()).map(UserStats::getLevel).orElse(1);
        Integer participants = post.getCategory() == Category.MATE
                ? (int) mateParticipantRepository.countByPostId(post.getPostId())
                : null;
        return PostDetailResponse.of(post, author, level, readContent(post.getContent()), myReaction, participants,
                readTags(post), post.getItinerary() != null ? readContent(post.getItinerary()) : null, myFork,
                readPlaces(post));
    }

    public JsonNode readContent(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException e) {
            throw new CommunityException(ErrorCode.INTERNAL_SERVER_ERROR, "내용을 읽을 수 없습니다.");
        }
    }

    /**
     * 장소 추천 글의 장소 목록.
     *
     * 장소를 하나만 담던 시절의 글은 places가 비어 있으므로 대표 장소 컬럼으로 한 건을 만들어 준다 —
     * 클라이언트가 "옛 글이면 다른 필드를 본다"를 몰라도 되게 하려는 것이다.
     */
    private List<RecommendPlace> readPlaces(Post post) {
        if (post.getCategory() != Category.RECOMMEND) {
            return null;
        }
        if (post.getPlaces() == null) {
            return post.getLocation() == null ? List.of() : List.of(RecommendPlace.ofLegacy(post));
        }
        try {
            return objectMapper.readValue(post.getPlaces(), new TypeReference<List<RecommendPlace>>() {});
        } catch (JsonProcessingException e) {
            // 저장된 JSON이 깨져도 상세 조회 전체를 실패시키지 않는다 — 대표 장소만으로 떨어진다
            return post.getLocation() == null ? List.of() : List.of(RecommendPlace.ofLegacy(post));
        }
    }

    /** 목록 카드의 "N곳" 배지 — 장소 추천 글은 담긴 장소 수, 피드는 일정에서 뽑은 수를 쓴다 */
    private PostSummaryResponse.PlacePreview readRecommendPlaceCount(Post post) {
        List<RecommendPlace> places = readPlaces(post);
        return places == null || places.size() <= 1
                ? PostSummaryResponse.PlacePreview.EMPTY
                : new PostSummaryResponse.PlacePreview(places.size(), null);
    }

    /**
     * 일정 JSON에서 날짜별 장소 이름을 추려낸다 — 목록 카드의 호버 팝업용.
     *
     * 일정이 없거나 깨져 있어도 목록 전체를 실패시키지 않는다(빈 미리보기로 떨어진다):
     * 상세 조회와 달리 목록은 게시글 하나가 부실하다고 응답을 못 줄 이유가 없다.
     */
    private PostSummaryResponse.PlacePreview readPlacePreview(Post post) {
        if (post.getCategory() == Category.RECOMMEND) {
            return readRecommendPlaceCount(post);
        }
        if (post.getCategory() != Category.FEED || post.getItinerary() == null) {
            return PostSummaryResponse.PlacePreview.EMPTY;
        }
        try {
            JsonNode days = objectMapper.readTree(post.getItinerary()).path("days");
            List<PostSummaryResponse.DayPlaces> byDay = new ArrayList<>();
            int total = 0;
            for (JsonNode day : days) {
                List<String> names = new ArrayList<>();
                int dayTotal = 0;
                for (JsonNode item : day.path("items")) {
                    String place = item.path("place").asText(null);
                    if (place == null || place.isBlank()) {
                        continue;
                    }
                    dayTotal++;
                    if (names.size() < PostSummaryResponse.PLACES_PER_DAY_LIMIT) {
                        names.add(place);
                    }
                }
                if (dayTotal == 0) {
                    continue;
                }
                total += dayTotal;
                // day 값이 비어 있으면 목록 순서(1부터)로 매긴다 — 팝업의 "Day N" 표기가 비지 않도록
                int dayNumber = day.path("day").asInt(byDay.size() + 1);
                byDay.add(new PostSummaryResponse.DayPlaces(dayNumber, dayTotal, names));
            }
            return total == 0
                    ? PostSummaryResponse.PlacePreview.EMPTY
                    : new PostSummaryResponse.PlacePreview(total, byDay);
        } catch (JsonProcessingException e) {
            return PostSummaryResponse.PlacePreview.EMPTY;
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
