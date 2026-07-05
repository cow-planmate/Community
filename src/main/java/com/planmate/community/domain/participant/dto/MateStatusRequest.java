package com.planmate.community.domain.participant.dto;

import jakarta.validation.constraints.NotBlank;

public record MateStatusRequest(
        @NotBlank(message = "상태 값은 필수입니다.")
        String status
) {
}
