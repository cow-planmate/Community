-- 장소 추천 게시판을 걷어내면서 V9/V10이 추가한 장소 스냅샷 열을 되돌린다.
-- (V9/V10은 이미 운영에 적용됐으므로 그 파일은 손대지 않는다 — 체크섬이 깨진다.)
--
-- rating/location/lat/lng 는 남긴다: location/lat/lng 는 FEED 여행지가 함께 쓰고,
-- rating 은 V1부터 있던 열이라 이 기능과 수명이 다르다.
ALTER TABLE community_post
    DROP COLUMN IF EXISTS place_address,
    DROP COLUMN IF EXISTS place_phone,
    DROP COLUMN IF EXISTS place_category,
    DROP COLUMN IF EXISTS place_url,
    DROP COLUMN IF EXISTS places;

-- 남아 있는 장소 추천 글은 자유게시판으로 옮긴다 — 카테고리가 사라져 조회가 안 되는 글이
-- 유령으로 남지 않도록.
UPDATE community_post SET category = 'FREE' WHERE category = 'RECOMMEND';
