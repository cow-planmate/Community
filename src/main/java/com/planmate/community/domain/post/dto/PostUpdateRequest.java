package com.planmate.community.domain.post.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

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

        // MATE 전용
        String region,
        Integer maxParticipants
) {
}
