package com.planmate.community.domain.post.dto;

import jakarta.validation.constraints.NotNull;

public record AnsweredRequest(
        @NotNull(message = "답변 완료 여부는 필수입니다.")
        Boolean isAnswered
) {
}
