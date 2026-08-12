-- ============================================================
-- Community V7__badge.sql
-- 활동 뱃지 달성 상태 저장 + 받은 좋아요 카운터
--
-- 뱃지는 조회할 때마다 집계하지 않는다. 활동이 일어나는 쓰기 트랜잭션에서
-- 진행도를 갱신해 두고(BadgeProgressService), 조회는 이 테이블 SELECT 한 번으로 끝낸다.
-- 레벨(community_user_stats.level)과 같은 방식이다.
-- ============================================================

-- ------------------------------------------------------------
-- 1. 받은 좋아요 카운터
--    마이페이지 "좋아요" 수와 베스트 파트너 뱃지가 같은 값을 쓴다.
--    반응 등록/해제 트랜잭션에서 원자적으로 증감한다.
-- ------------------------------------------------------------
ALTER TABLE community_user_stats
    ADD COLUMN received_likes INT NOT NULL DEFAULT 0;

UPDATE community_user_stats s
SET received_likes = COALESCE((
        SELECT SUM(p.like_count) FROM community_post p
        WHERE p.user_id = s.user_id AND p.deleted_at IS NULL
    ), 0),
    updated_at = now();

-- ------------------------------------------------------------
-- 2. 뱃지 진행도/달성 상태
--    badge_code 는 BadgeType enum 의 소문자 이름이다.
--    한 번 달성한 뱃지는 회수하지 않으므로 earned_at 은 채워진 뒤 바뀌지 않는다
--    (글이 지워져 progress 가 내려가도 유지).
-- ------------------------------------------------------------
CREATE TABLE community_user_badge (
    user_id    UUID        NOT NULL,
    badge_code VARCHAR(32) NOT NULL,
    progress   INT         NOT NULL DEFAULT 0,
    earned_at  TIMESTAMP,
    updated_at TIMESTAMP   NOT NULL,
    PRIMARY KEY (user_id, badge_code)
);

-- ------------------------------------------------------------
-- 3. 기존 사용자 백필
--    활동 이력이 있는 사용자(통계·게시글·댓글 어디에든 등장) 전체를 대상으로
--    현재 수치를 한 번 계산해 넣는다. 이후로는 쓰기 시점 갱신만 일어난다.
-- ------------------------------------------------------------
WITH users AS (
    SELECT user_id FROM community_user_stats
    UNION
    SELECT DISTINCT user_id FROM community_post WHERE deleted_at IS NULL
    UNION
    SELECT DISTINCT user_id FROM community_comment WHERE deleted_at IS NULL
),
metrics AS (
    SELECT u.user_id,
           COALESCE((SELECT COUNT(*) FROM community_post p
                     WHERE p.user_id = u.user_id AND p.deleted_at IS NULL), 0)::INT AS post_count,
           COALESCE((SELECT COUNT(*) FROM community_post p
                     WHERE p.user_id = u.user_id AND p.deleted_at IS NULL AND p.category = 'FEED'), 0)::INT AS feed_count,
           COALESCE((SELECT COUNT(*) FROM community_comment c
                     WHERE c.user_id = u.user_id AND c.deleted_at IS NULL), 0)::INT AS comment_count,
           COALESCE((SELECT SUM(p.like_count) FROM community_post p
                     WHERE p.user_id = u.user_id AND p.deleted_at IS NULL), 0)::INT AS received_likes,
           COALESCE((SELECT COUNT(DISTINCT p.region) FROM community_post p
                     WHERE p.user_id = u.user_id AND p.deleted_at IS NULL
                       AND p.category = 'FEED' AND p.region IS NOT NULL), 0)::INT AS region_count
    FROM users u
)
INSERT INTO community_user_badge (user_id, badge_code, progress, earned_at, updated_at)
SELECT m.user_id,
       b.code,
       LEAST(b.value, b.goal),
       CASE WHEN b.value >= b.goal THEN now() END,
       now()
FROM metrics m
CROSS JOIN LATERAL (VALUES
    ('first_step',     m.post_count,     1),
    ('plan_master',    m.feed_count,     5),
    ('eager_reviewer', m.comment_count, 20),
    ('best_partner',   m.received_likes, 50),
    ('nationwide',     m.region_count,   5)
) AS b(code, value, goal);
