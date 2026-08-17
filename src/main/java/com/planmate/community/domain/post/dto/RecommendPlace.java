package com.planmate.community.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.planmate.community.domain.post.entity.Post;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 장소 추천 글에 담긴 장소 한 건.
 *
 * 카카오 로컬 검색 결과를 그대로 스냅샷으로 저장한다 — 나중에 카카오가 place id 체계를 바꾸거나
 * 장소가 폐업해도 글에 적힌 정보는 남아 있어야 하기 때문이다.
 *
 * @param memo 작성자가 장소별로 남긴 한 줄 코멘트 (선택)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecommendPlace(
        @NotBlank(message = "장소 이름은 필수입니다.")
        @Size(max = 255, message = "장소 이름은 255자를 넘을 수 없습니다.")
        String name,

        @Size(max = 255)
        String address,

        @Size(max = 32)
        String phone,

        @Size(max = 255)
        String category,

        @Size(max = 512)
        String url,

        Double lat,

        Double lng,

        @Size(max = 500, message = "장소 메모는 500자를 넘을 수 없습니다.")
        String memo,

        /** 장소별 평점 (0.0~5.0). 글 전체 평점은 여기 매긴 값들의 평균이 된다 */
        @DecimalMin(value = "0.0", message = "평점은 0.0에서 5.0 사이여야 합니다.")
        @DecimalMax(value = "5.0", message = "평점은 0.0에서 5.0 사이여야 합니다.")
        BigDecimal rating
) {

    /** places가 비어 있는 옛 글 — 대표 장소 컬럼만으로 한 건을 만들어 준다 */
    public static RecommendPlace ofLegacy(Post post) {
        return new RecommendPlace(
                post.getLocation(),
                post.getPlaceAddress(),
                post.getPlacePhone(),
                post.getPlaceCategory(),
                post.getPlaceUrl(),
                post.getLat(),
                post.getLng(),
                null,
                post.getRating()
        );
    }
}
