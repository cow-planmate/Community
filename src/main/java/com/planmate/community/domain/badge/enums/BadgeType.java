package com.planmate.community.domain.badge.enums;

import java.util.Locale;

/**
 * 커뮤니티 활동 뱃지 카탈로그.
 *
 * 달성 여부는 저장하지 않고 활동 집계(BadgeMetrics)로 조회 시점에 판정한다.
 * 기준을 바꿔도 마이그레이션이나 재계산 배치가 필요 없고, 카운터가 유일한 진실 원천으로 남는다.
 */
public enum BadgeType {

    FIRST_STEP("첫 걸음", "커뮤니티에 첫 글을 작성", 1),
    PLAN_MASTER("계획의 달인", "여행기 5편 작성", 5),
    EAGER_REVIEWER("열혈 리뷰어", "댓글 20개 작성", 20),
    BEST_PARTNER("베스트 파트너", "내 글이 받은 좋아요 50개", 50),
    NATIONWIDE("전국 제패", "서로 다른 지역 5곳의 여행기 작성", 5);

    private final String displayName;
    private final String description;
    private final int goal;

    BadgeType(String displayName, String description, int goal) {
        this.displayName = displayName;
        this.description = description;
        this.goal = goal;
    }

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public int goal() {
        return goal;
    }
}
