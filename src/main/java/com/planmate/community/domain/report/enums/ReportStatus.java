package com.planmate.community.domain.report.enums;

/** 신고 처리 상태. DB 의 post_report.status 와 같은 값을 쓴다. */
public enum ReportStatus {
    PENDING, RESOLVED, DISMISSED
}
