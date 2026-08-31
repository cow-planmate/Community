-- feed_post 에 있던 커뮤니티 전용 컬럼 제거.
--
-- V12 에서 FeedPost 가 @MappedSuperclass Post 를 상속하면서, QNA/MATE/RECOMMEND 전용 필드가
-- feed_post 에도 그대로 생겼다. 이 컬럼들은 만들어진 이래 한 번도 값이 들어간 적이 없다
-- (피드 INSERT 경로가 채우지 않는다). 엔티티에서 CommunityPost 로 필드를 내렸으므로 컬럼도 지운다.
--
-- 저장 공간보다 스키마 계약이 목적이다 — feed_post 를 읽는 사람이 피드에 평점·장소 전화번호가
-- 있는 줄 알게 되고, 코드 실수로 값이 들어가도 막을 제약이 없었다.

-- 값이 실제로 비어 있는지 확인한다. 하나라도 차 있으면 마이그레이션을 중단한다.
DO $$
DECLARE
    dirty BIGINT;
BEGIN
    SELECT count(*) INTO dirty FROM feed_post
    WHERE is_answered IS NOT NULL
       OR max_participants IS NOT NULL
       OR status IS NOT NULL
       OR rating IS NOT NULL
       OR place_address IS NOT NULL
       OR place_phone IS NOT NULL
       OR place_category IS NOT NULL
       OR place_url IS NOT NULL
       OR places IS NOT NULL;

    IF dirty > 0 THEN
        RAISE EXCEPTION 'feed_post 의 커뮤니티 전용 컬럼에 값이 있는 행 % 건 — 확인 후 진행하세요.', dirty;
    END IF;
END $$;

ALTER TABLE feed_post
    DROP COLUMN is_answered,
    DROP COLUMN max_participants,
    DROP COLUMN status,
    DROP COLUMN rating,
    DROP COLUMN place_address,
    DROP COLUMN place_phone,
    DROP COLUMN place_category,
    DROP COLUMN place_url,
    DROP COLUMN places;
