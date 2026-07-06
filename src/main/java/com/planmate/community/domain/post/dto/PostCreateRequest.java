package com.planmate.community.domain.post.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PostCreateRequest(
        @NotBlank(message = "카테고리는 필수입니다.")
        String category,

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 255, message = "제목은 255자를 넘을 수 없습니다.")
        String title,

        @NotNull(message = "내용은 필수입니다.")
        JsonNode content,

        String contentText,

        String thumbnailUrl,

        // RECOMMEND 전용
        String location,
        BigDecimal rating,
        Double lat,
        Double lng,

        // MATE 전용
        String region,
        Integer maxParticipants,

        // FEED 전용 (region은 MATE와 공용)
        Integer durationDays,
        JsonNode itinerary,
        List<String> tags,
        UUID sourcePlanId
) {
}
