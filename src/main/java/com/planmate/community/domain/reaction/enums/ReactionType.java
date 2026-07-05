package com.planmate.community.domain.reaction.enums;

import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;

import java.util.Locale;

public enum ReactionType {

    LIKE, DISLIKE;

    public static ReactionType from(String value) {
        if (value == null || value.isBlank()) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "반응 타입은 필수입니다.");
        }
        try {
            return ReactionType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "존재하지 않는 반응 타입입니다: " + value);
        }
    }

    public String toLowerValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
