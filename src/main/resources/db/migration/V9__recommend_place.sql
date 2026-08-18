-- 장소 추천 글의 위치를 자유 텍스트 → 카카오 로컬 검색 결과로 저장하기 위한 열.
-- 기존 글은 location(문자열)만 있고 나머지는 NULL이다. 프론트는 NULL을 안 그리므로 그대로 둔다.
ALTER TABLE community_post
    ADD COLUMN IF NOT EXISTS place_address  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS place_phone    VARCHAR(32),
    ADD COLUMN IF NOT EXISTS place_category VARCHAR(255),
    ADD COLUMN IF NOT EXISTS place_url      VARCHAR(512);
