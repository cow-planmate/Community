package com.planmate.community.domain.post.enums;

import org.springframework.data.domain.Sort;

import java.util.Locale;

public enum SortType {

    LATEST, LIKES, VIEWS, FORKS;

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

    /**
     * 정렬 방향을 적용한 Sort. 방향은 1차 정렬 키에만 반영하고, 2차 키(createdAt)는
     * 값이 같을 때의 tie-break일 뿐이므로 항상 최신순으로 고정한다 —
     * 좋아요 수가 같은 글들이 오름차순이라고 오래된 순으로 뒤집힐 이유는 없다.
     */
    public Sort toSort(Sort.Direction direction) {
        Sort.Order primary = new Sort.Order(direction, primaryProperty());
        return this == LATEST
                ? Sort.by(primary)
                : Sort.by(primary, Sort.Order.desc("createdAt"));
    }

    private String primaryProperty() {
        return switch (this) {
            case LATEST -> "createdAt";
            case LIKES -> "likeCount";
            case VIEWS -> "viewCount";
            case FORKS -> "forkCount";
        };
    }

    /** 정렬 방향 파라미터 파싱. SortType.from과 같이 잘못된 값은 조용히 기본값(내림차순)으로 떨어뜨린다. */
    public static Sort.Direction direction(String value) {
        return value != null && "asc".equalsIgnoreCase(value.trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
    }
}
