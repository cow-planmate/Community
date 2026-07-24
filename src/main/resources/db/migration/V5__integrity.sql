-- ============================================================
-- Community V5__integrity.sql
-- 카테고리별 컬럼 정합성을 DB 제약으로 승격 + 좌표 타입 정합
-- 제약 조건은 PostService.createPost / validateCategoryFields 가
-- 이미 보장하는 불변식과 정확히 일치한다 (기존 앱 데이터는 위반하지 않음).
-- ============================================================

-- ------------------------------------------------------------
-- 1. 좌표 타입 정합 — Backend-v2(latitude/longitude)와 동일 타입으로 통일
--    (기존 DOUBLE PRECISION → NUMERIC). 위도 ±90 → (10,8), 경도 ±180 → (11,8)
-- ------------------------------------------------------------
ALTER TABLE community_post
    ALTER COLUMN lat TYPE NUMERIC(10, 8) USING lat::NUMERIC(10, 8),
    ALTER COLUMN lng TYPE NUMERIC(11, 8) USING lng::NUMERIC(11, 8);

-- ------------------------------------------------------------
-- 2. 카테고리별 "필수" 필드 제약 (presence)
--    createPost 에서 항상 채워지는 값 = DB에서도 보장
-- ------------------------------------------------------------
ALTER TABLE community_post
    ADD CONSTRAINT chk_post_qna_required
        CHECK (category <> 'QNA' OR is_answered IS NOT NULL),
    ADD CONSTRAINT chk_post_mate_required
        CHECK (category <> 'MATE' OR (region IS NOT NULL AND status IS NOT NULL)),
    ADD CONSTRAINT chk_post_recommend_required
        CHECK (category <> 'RECOMMEND' OR (location IS NOT NULL AND rating IS NOT NULL)),
    ADD CONSTRAINT chk_post_feed_required
        CHECK (category <> 'FEED' OR (region IS NOT NULL AND duration_days IS NOT NULL));

-- ------------------------------------------------------------
-- 3. 카테고리별 "전용" 필드 제약 (scope) — 다른 카테고리에는 NULL 이어야 함
--    createPost 는 해당 카테고리가 아니면 명시적으로 null 을 넣는다.
--    region       : MATE / FEED
--    location/lat/lng : RECOMMEND / FEED
--    max_participants/status : MATE
--    rating       : RECOMMEND
--    is_answered  : QNA
--    duration_days/itinerary/tags/source_plan_id : FEED
--    (fork_count 는 전 카테고리 공통 DEFAULT 0 이므로 제약 없음)
-- ------------------------------------------------------------
ALTER TABLE community_post
    ADD CONSTRAINT chk_post_qna_scope
        CHECK (category = 'QNA' OR is_answered IS NULL),
    ADD CONSTRAINT chk_post_mate_scope
        CHECK (category = 'MATE' OR (max_participants IS NULL AND status IS NULL)),
    ADD CONSTRAINT chk_post_recommend_scope
        CHECK (category = 'RECOMMEND' OR rating IS NULL),
    ADD CONSTRAINT chk_post_region_scope
        CHECK (category IN ('MATE', 'FEED') OR region IS NULL),
    ADD CONSTRAINT chk_post_geo_scope
        CHECK (category IN ('RECOMMEND', 'FEED') OR (location IS NULL AND lat IS NULL AND lng IS NULL)),
    ADD CONSTRAINT chk_post_feed_scope
        CHECK (category = 'FEED' OR (duration_days IS NULL AND itinerary IS NULL AND tags IS NULL AND source_plan_id IS NULL));
