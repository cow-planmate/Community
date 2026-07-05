package com.planmate.community.domain.reaction.dto;

import jakarta.validation.constraints.NotBlank;

public record ReactionRequest(
        @NotBlank(message = "반응 타입은 필수입니다.")
        String type
) {
}
