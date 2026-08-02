-- ============================================================
-- 사용자 읽기 모델 (Backend-v2 소유 데이터의 로컬 복제본)
--
-- 왜 캐시가 아니라 테이블인가:
--   1) 작성자 속성으로 SQL 필터를 걸어야 한다. PageResponse 의 totalElements/totalPages 는
--      DB count 쿼리에서 나오므로, 페이지를 가져온 뒤 애플리케이션에서 거르면 페이지네이션이
--      깨진다(짧은 페이지, 틀린 총계). 필터는 정렬·LIMIT 과 같은 문장 안에 있어야 한다.
--   2) Backend-v2 가 죽어도 목록·상세·글쓰기가 정상 동작해야 한다.
--   3) 이 서비스의 Redis 는 --save "" --appendonly no 로 뜨는 순수 캐시라 복제본을 못 둔다.
--
-- 채우는 방법: Backend-v2 의 user_outbox 를 WatchUserChanges 스트림으로 재생한다.
-- 최초 1회는 전체 스냅샷을 받고, 이후에는 커서(last_applied_seq) 다음부터 이어받는다.
-- ============================================================

CREATE TABLE community_user (
    user_id           UUID PRIMARY KEY,

    -- 탈퇴 계정은 NULL 이다. 화면에는 "탈퇴한 사용자"로 표시하고 프로필 링크를 걸지 않는다.
    nickname          VARCHAR(100),
    profile_image_url VARCHAR(500),
    avatar_hash       VARCHAR(32),

    -- 기본 비공개. Backend-v2 의 users.profile_public 과 같은 기본값을 유지한다 —
    -- 행이 아직 복제되지 않은 사용자를 공개로 오인하면 안 된다.
    profile_public    BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted           BOOLEAN   NOT NULL DEFAULT FALSE,

    -- 이 행에 반영된 outbox 시퀀스. 업서트가 source_seq 가 더 큰 갱신만 적용하므로,
    -- 중복 수신이나 재생 순서가 뒤바뀌어도 옛 값이 새 값을 덮지 못한다(멱등).
    source_seq        BIGINT    NOT NULL,

    updated_at        TIMESTAMP NOT NULL
);

-- 작성자 닉네임 검색용. community_post 의 title/content_text 와 같은 pg_trgm 패턴
-- (확장은 V1__init.sql 에서 이미 설치했다).
CREATE INDEX idx_community_user_nickname_trgm ON community_user USING GIN (nickname gin_trgm_ops);

-- ------------------------------------------------------------
-- 복제 커서. 단일 행(id = 1) 고정.
--
-- snapshot_complete 가 false 인 동안에는 프로젝션이 불완전하다. 그 구간에는
--   - 신규 필터를 켜면 안 되고(틀린 결과를 주느니 파라미터를 무시한다),
--   - 공개 여부 판정은 기존 gRPC 경로를 써야 하며,
--   - 작성자 표시는 게시글에 저장된 닉네임 스냅샷으로 떨어진다.
-- 즉 백필이 끝나기 전 동작은 이 변경 이전과 완전히 같다.
--
-- 커서는 스냅샷이 끝났다는 표시를 받은 시점에만 올린다. 스냅샷 도중에 올리면
-- 그 사이 죽었을 때 아직 반영 안 한 사용자들을 영영 건너뛴다.
-- ------------------------------------------------------------
CREATE TABLE community_user_replication (
    id                SMALLINT  PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    last_applied_seq  BIGINT    NOT NULL DEFAULT 0,
    snapshot_complete BOOLEAN   NOT NULL DEFAULT FALSE,
    updated_at        TIMESTAMP NOT NULL
);

INSERT INTO community_user_replication (id, last_applied_seq, snapshot_complete, updated_at)
VALUES (1, 0, FALSE, now());
