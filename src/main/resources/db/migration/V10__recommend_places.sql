-- 장소 추천 글 하나에 여러 장소를 담기 위한 열 ("일산 카페들" 같은 묶음 글).
-- 배열의 첫 번째가 대표 장소이고, 기존 location/lat/lng/place_* 컬럼에 그대로 미러링된다.
-- (목록 배지·지도 보기 등 대표 장소만 쓰는 화면을 건드리지 않기 위해서다.)
-- 장소가 하나뿐인 옛 글은 places가 NULL이고, 조회 시 대표 장소 한 건으로 취급한다.
ALTER TABLE community_post
    ADD COLUMN IF NOT EXISTS places jsonb;
