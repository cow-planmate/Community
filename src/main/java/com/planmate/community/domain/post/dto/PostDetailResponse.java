package com.planmate.community.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.planmate.community.common.client.AuthorProfile;
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
        /** 작성자 프로필 사진 URL. 없으면 클라이언트가 authorAvatarHash(Gravatar) → 이니셜 순으로 떨어진다 */
        String authorImage,
        /** 작성자 이메일 해시(Gravatar 식별자). 이메일 원문은 내려오지 않는다 */
        String authorAvatarHash,
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

    public static PostDetailResponse of(Post post, AuthorProfile author, int level, JsonNode content, String myReaction,
                                        Integer participants, List<String> tags, JsonNode itinerary, Boolean myFork) {
        AuthorProfile resolved = AuthorProfile.resolve(author, post.getAuthorNickname());
        return new PostDetailResponse(
                post.getPostId(),
                post.getUserId(),
                post.getCategory().toLowerValue(),
                post.getTitle(),
                resolved.nickname(),
                resolved.profileImageUrl(),
                resolved.avatarHash(),
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
