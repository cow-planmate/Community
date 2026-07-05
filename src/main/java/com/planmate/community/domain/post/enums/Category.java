package com.planmate.community.domain.post.enums;

import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;

import java.util.Locale;

public enum Category {

    FREE, QNA, MATE, RECOMMEND;

    public static Category from(String value) {
        if (value == null || value.isBlank()) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "카테고리는 필수입니다.");
        }
        try {
            return Category.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "존재하지 않는 게시판입니다: " + value);
        }
    }

    public String toLowerValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
