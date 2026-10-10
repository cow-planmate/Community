package com.planmate.community.domain.admin.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 관리 대상 종류. 테이블 이름과 신고 참조 컬럼은 여기서만 정한다 — SQL 문자열에 요청 값이 섞이지 않게 하려는 것.
 */
@Getter
@RequiredArgsConstructor
public enum ModerationTargetType {

    POST("community_post", "post_id", "community_comment", "community_reaction"),
    FEED("feed_post", "feed_id", "feed_comment", "feed_reaction");

    /** 글 테이블 */
    private final String table;
    /** post_report 에서 이 대상을 가리키는 컬럼 */
    private final String reportColumn;
    /** 댓글 테이블 (글 자식) */
    private final String commentTable;
    /** 반응(좋아요/싫어요) 테이블 */
    private final String reactionTable;
}
