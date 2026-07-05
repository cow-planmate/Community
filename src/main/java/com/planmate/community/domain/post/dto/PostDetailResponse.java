package com.planmate.community.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.planmate.community.domain.post.entity.Post;

import java.time.LocalDateTime;
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

        // RECOMMEND 전용
        String location,
        String rating,
        PostSummaryResponse.Coords coords,

        // 로그인 사용자의 반응 (like|dislike|null)
        String myReaction
) {

    public static PostDetailResponse of(Post post, String freshNickname, int level, JsonNode content, String myReaction, Integer participants) {
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
                post.getLocation(),
                post.getRating() != null ? post.getRating().toPlainString() : null,
                post.getLat() != null && post.getLng() != null
                        ? new PostSummaryResponse.Coords(post.getLat(), post.getLng())
                        : null,
                myReaction
        );
    }
}
