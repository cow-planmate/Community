package com.planmate.community.domain.post.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record PostUpdateRequest(
        @Size(max = 255, message = "제목은 255자를 넘을 수 없습니다.")
        String title,

        JsonNode content,

        String contentText,

        String thumbnailUrl,

        // RECOMMEND 전용
        String location,
        BigDecimal rating,
        Double lat,
        Double lng,
        String placeAddress,
        String placePhone,
        String placeCategory,
        String placeUrl,
        /** 장소 목록 — 넘어온 경우에만 통째로 교체한다 (null이면 변경 없음) */
        @Valid
        List<RecommendPlace> places,

        // MATE 전용 (region은 FEED에서도 사용)
        String region,
        Integer maxParticipants,

        // FEED 전용 (location도 함께 사용)
        Integer durationDays,
        JsonNode itinerary,
        List<String> tags
) {
}
