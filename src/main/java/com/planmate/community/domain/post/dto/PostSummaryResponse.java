package com.planmate.community.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.enums.Category;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostSummaryResponse(
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
        String image,

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
        Coords coords,

        // FEED 전용 (비-FEED는 null로 응답에서 생략)
        Integer durationDays,
        Integer forks,
        List<String> tags
) {

    public record Coords(double lat, double lng) {
    }

    public static PostSummaryResponse of(Post post, String freshNickname, int level, Integer participants, List<String> tags) {
        return new PostSummaryResponse(
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
                post.getThumbnailUrl(),
                post.getIsAnswered(),
                participants,
                post.getMaxParticipants(),
                post.getStatus() != null ? post.getStatus().toLowerValue() : null,
                post.getRegion(),
                post.getLocation(),
                post.getRating() != null ? post.getRating().toPlainString() : null,
                post.getLat() != null && post.getLng() != null ? new Coords(post.getLat(), post.getLng()) : null,
                post.getDurationDays(),
                post.getCategory() == Category.FEED ? post.getForkCount() : null,
                tags
        );
    }
}
