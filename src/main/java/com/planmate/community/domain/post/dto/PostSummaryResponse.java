package com.planmate.community.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.planmate.community.common.client.AuthorProfile;
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
        /** 작성자 프로필 사진 URL. 없으면 클라이언트가 authorAvatarHash(Gravatar) → 이니셜 순으로 떨어진다 */
        String authorImage,
        /** 작성자 이메일 해시(Gravatar 식별자). 이메일 원문은 내려오지 않는다 */
        String authorAvatarHash,
        /** 탈퇴한 사용자. true면 author는 "탈퇴한 사용자"이고 프로필로 이동할 수 없다 */
        boolean authorDeleted,
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
        List<String> tags,
        String description,
        /** 일정에 담긴 전체 장소 수 — 목록 카드에서 "장소 N곳"으로 쓴다 */
        Integer placeCount,
        /** 날짜별 장소 미리보기 — 목록 카드 호버 팝업이 Day를 넘겨가며 보여준다 */
        List<DayPlaces> placesByDay,

        // 내 활동 목록 전용 — 좋아요/가져가기를 한 시각 (그 외 목록에서는 null로 생략)
        LocalDateTime actedAt
) {

    private static final int DESCRIPTION_PREVIEW_LENGTH = 200;

    /** 목록 응답이 무거워지지 않도록 하루치 장소는 앞에서 이만큼만 내려준다 */
    public static final int PLACES_PER_DAY_LIMIT = 8;

    public record Coords(double lat, double lng) {
    }

    /**
     * 하루치 장소 미리보기.
     *
     * @param count  그날의 전체 장소 수 (places보다 클 수 있다)
     * @param places 앞에서 {@value #PLACES_PER_DAY_LIMIT}개까지의 장소 이름
     */
    public record DayPlaces(int day, int count, List<String> places) {
    }

    /** 일정에서 뽑아낸 장소 요약 — JSON 파싱은 ObjectMapper를 가진 PostAssembler가 한다 */
    public record PlacePreview(Integer count, List<DayPlaces> byDay) {
        public static final PlacePreview EMPTY = new PlacePreview(null, null);
    }

    public static PostSummaryResponse of(Post post, AuthorProfile author, int level, Integer participants, List<String> tags,
                                         PlacePreview places) {
        AuthorProfile resolved = AuthorProfile.resolve(author, post.getAuthorNickname());
        return new PostSummaryResponse(
                post.getPostId(),
                post.getUserId(),
                post.getCategory().toLowerValue(),
                post.getTitle(),
                resolved.nickname(),
                resolved.profileImageUrl(),
                resolved.avatarHash(),
                resolved.deleted(),
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
                tags,
                post.getCategory() == Category.FEED ? previewOf(post.getContentText()) : null,
                places.count(),
                places.byDay(),
                null
        );
    }

    public PostSummaryResponse withActedAt(LocalDateTime actedAt) {
        return new PostSummaryResponse(
                id, userId, category, title, author, authorImage, authorAvatarHash, authorDeleted, level, likes, dislikes, comments,
                views, createdAt, image,
                isAnswered, participants, maxParticipants, status, region, location, rating, coords,
                durationDays, forks, tags, description, placeCount, placesByDay, actedAt
        );
    }

    // 피드 카드 본문 미리보기 — contentText 앞부분만 잘라 목록 페이로드를 가볍게 유지
    private static String previewOf(String contentText) {
        if (contentText == null || contentText.isBlank()) {
            return null;
        }
        String trimmed = contentText.strip();
        return trimmed.length() <= DESCRIPTION_PREVIEW_LENGTH ? trimmed : trimmed.substring(0, DESCRIPTION_PREVIEW_LENGTH);
    }
}
