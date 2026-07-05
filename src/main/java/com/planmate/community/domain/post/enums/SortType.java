package com.planmate.community.domain.post.enums;

import org.springframework.data.domain.Sort;

import java.util.Locale;

public enum SortType {

    LATEST, LIKES, VIEWS;

    public static SortType from(String value) {
        if (value == null || value.isBlank()) {
            return LATEST;
        }
        try {
            return SortType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return LATEST;
        }
    }

    public Sort toSort() {
        return switch (this) {
            case LATEST -> Sort.by(Sort.Order.desc("createdAt"));
            case LIKES -> Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"));
            case VIEWS -> Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("createdAt"));
        };
    }
}
