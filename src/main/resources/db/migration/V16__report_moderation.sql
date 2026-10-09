-- 신고 처리 상태: 대기(PENDING) → 처리 완료(RESOLVED) 또는 기각(DISMISSED)
ALTER TABLE post_report
    ADD COLUMN status     VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RESOLVED', 'DISMISSED')),
    ADD COLUMN handled_by UUID,
    ADD COLUMN handled_at TIMESTAMP;

CREATE INDEX idx_post_report_status_created ON post_report (status, created_at DESC);

-- 관리자 행위 기록 (글 삭제·복구·신고 기각). 정지·해제는 Backend-v2 admin_action_log 에 남는다.
CREATE TABLE community_admin_action_log (
    log_id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_user_id UUID         NOT NULL,
    action_type   VARCHAR(32)  NOT NULL
        CHECK (action_type IN ('DISMISS_REPORTS', 'DELETE_CONTENT', 'RESTORE_CONTENT')),
    target_type   VARCHAR(16)  NOT NULL CHECK (target_type IN ('POST', 'FEED')),
    target_id     BIGINT       NOT NULL,
    reason        VARCHAR(255),
    created_at    TIMESTAMP    NOT NULL
);

CREATE INDEX idx_community_admin_action_log_created ON community_admin_action_log (created_at DESC);
