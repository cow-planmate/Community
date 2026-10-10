package com.planmate.community.domain.admin.dto;

import jakarta.validation.constraints.Size;

/** 관리 조치 사유. 감사 로그에만 남으며 본문은 없어도 된다. */
public record ModerationActionRequest(
        @Size(max = 255, message = "사유는 255자를 넘을 수 없습니다.")
        String reason
) {
}
