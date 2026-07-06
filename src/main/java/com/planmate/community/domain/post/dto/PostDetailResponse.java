package com.planmate.community.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostDetailResponse(
        Long id,
        UUID userId,
        String category,
        String title,
        String author,
        int level,
        int likes,
        int dislikes,
        int comments,
        int views,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String image,
        JsonNode content,
        String contentText,

        // QNA 전용
        Boolean isAnswered,

        // MATE 전용
        Integer participants,
        Integer maxParticipants,
        String status,
        String region,

        // RECOMMEND 전용
        String location,
        String rating,
        PostSummaryResponse.Coords coords,

        // FEED 전용 (비-FEED는 null로 응답에서 생략)
        Integer durationDays,
        Integer forks,
        List<String> tags,
        JsonNode itinerary,
        UUID sourcePlanId,
        Boolean myFork,

        // 로그인 사용자의 반응 (like|dislike|null)
        String myReaction
) {

    public static PostDetailResponse of(Post post, String freshNickname, int level, JsonNode content, String myReaction,
                                        Integer participants, List<String> tags, JsonNode itinerary, Boolean myFork) {
        return new PostDetailResponse(
                post.getPostId(),
                post.getUserId(),
                post.getCategory().toLowerValue(),
                post.getTitle(),
                freshNickname != null ? freshNickname : post.getAuthorNickname(),
                level,
                post.getLikeCount(),
                post.getDislikeCount(),
                post.getCommentCount(),
                post.getViewCount(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getThumbnailUrl(),
                content,
                post.getContentText(),
                post.getIsAnswered(),
                participants,
                post.getMaxParticipants(),
                post.getStatus() != null ? post.getStatus().toLowerValue() : null,
                post.getRegion(),
                post.getLocation(),
                post.getRating() != null ? post.getRating().toPlainString() : null,
                post.getLat() != null && post.getLng() != null
                        ? new PostSummaryResponse.Coords(post.getLat(), post.getLng())
                        : null,
                post.getDurationDays(),
                post.getCategory() == Category.FEED ? post.getForkCount() : null,
                tags,
                itinerary,
                post.getSourcePlanId(),
                myFork,
                myReaction
        );
    }
}
