-- 커뮤니티 전용 DB 계정 생성 + v2 DB 차단
--
-- 지금까지 커뮤니티는 Backend-v2 와 같은 Postgres 계정(${POSTGRES_USER}, 사실상 superuser)을
-- 썼다. DB 이름만 다를 뿐 권한이 같아서, 실수로든 버그로든 planmate2 의 테이블을 읽고 쓸 수
-- 있는 상태였다. 인스턴스를 쪼개는 대신 권한으로 가른다 — 이 규모에서 인스턴스 이중화는
-- 백업/모니터링 비용만 늘린다.
--
-- 실행 (docker53):
--   docker exec -i planmate-postgres psql -U planmate -v ON_ERROR_STOP=1 \
--     -v community_password="'실제_비밀번호'" < community-db-account.sql
--
--   -U 는 운영의 superuser 롤이다. 이 스택은 postgres 이미지를 POSTGRES_USER=planmate 로
--   초기화했으므로 'postgres' 가 아니라 'planmate' 다.
--   비밀번호는 반드시 작은따옴표까지 포함해 넘긴다 (-v 는 문자열 치환이라 따옴표가 필요하다).
--
-- 되돌리기는 파일 맨 아래 주석 참고.

\set ON_ERROR_STOP on

-- ── 1. 롤 ────────────────────────────────────────────────────────────────────
-- 이미 있으면 비밀번호만 갱신한다(재실행 가능해야 운영에서 쓸 수 있다).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'community_app') THEN
        RAISE NOTICE 'community_app 이 이미 있다 — 비밀번호만 갱신한다';
    ELSE
        CREATE ROLE community_app LOGIN;
    END IF;
END
$$;

ALTER ROLE community_app WITH LOGIN PASSWORD :community_password;

-- ── 2. planmate2 차단 ────────────────────────────────────────────────────────
-- 여기가 이 스크립트의 핵심이다. Postgres 는 기본적으로 PUBLIC 에 모든 DB 의 CONNECT 를
-- 준다. 그래서 롤만 만들고 끝내면 community_app 이 planmate2 에 그냥 붙는다 —
-- "GRANT 를 안 줬으니 못 붙겠지"가 통하지 않는다.
--
-- Backend-v2 는 superuser(planmate) 로 붙으므로 이 REVOKE 의 영향을 받지 않는다.
-- 나중에 v2 도 전용 계정으로 내리면 그 계정에 명시적으로 GRANT 해야 한다.
--
-- 없는 DB 를 REVOKE 하면 ON_ERROR_STOP 때문에 스크립트가 통째로 중단된다. 운영에는
-- planmate 만 있고 planmate2 는 로컬에만 있는 식으로 환경마다 다르므로, 있는 것만 처리한다.
DO $$
DECLARE db text;
BEGIN
    FOREACH db IN ARRAY ARRAY['planmate', 'planmate2'] LOOP
        IF EXISTS (SELECT 1 FROM pg_database WHERE datname = db) THEN
            EXECUTE format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', db);
            RAISE NOTICE '% : PUBLIC 의 CONNECT 를 회수했다', db;
        ELSE
            RAISE NOTICE '% : 없음 — 건너뛴다', db;
        END IF;
    END LOOP;
END
$$;

-- ── 3. community DB 접근 ─────────────────────────────────────────────────────
GRANT CONNECT ON DATABASE community TO community_app;

\connect community

-- Flyway 가 마이그레이션에서 테이블을 만들려면 스키마에 CREATE 가 필요하다.
GRANT USAGE, CREATE ON SCHEMA public TO community_app;

-- 기존 객체는 postgres 소유다. Flyway 가 앞으로 ALTER TABLE / DROP INDEX 를 하려면
-- 소유자여야 하므로 넘긴다. GRANT ALL 로는 ALTER 가 안 된다 — 이걸 빠뜨리면 다음
-- 마이그레이션이 "must be owner of table" 로 죽는다.
DO $$
DECLARE
    obj record;
BEGIN
    FOR obj IN SELECT tablename FROM pg_tables WHERE schemaname = 'public'
    LOOP
        EXECUTE format('ALTER TABLE public.%I OWNER TO community_app', obj.tablename);
    END LOOP;

    FOR obj IN SELECT sequencename FROM pg_sequences WHERE schemaname = 'public'
    LOOP
        EXECUTE format('ALTER SEQUENCE public.%I OWNER TO community_app', obj.sequencename);
    END LOOP;

    FOR obj IN SELECT viewname FROM pg_views WHERE schemaname = 'public'
    LOOP
        EXECUTE format('ALTER VIEW public.%I OWNER TO community_app', obj.viewname);
    END LOOP;
END
$$;

-- pg_trgm 등 확장은 postgres 소유로 남긴다 — 확장 소유권 이전은 superuser 만 가능하고,
-- 쓰는 데는 소유권이 필요 없다.

-- ── 4. 확인 ──────────────────────────────────────────────────────────────────
\echo ''
\echo '== 확인 =='
SELECT 'community 접근(t 여야 정상)' AS check,
       has_database_privilege('community_app', 'community', 'CONNECT')::text AS result
UNION ALL
SELECT d.datname || ' 차단(f 여야 정상)',
       has_database_privilege('community_app', d.datname, 'CONNECT')::text
  FROM pg_database d WHERE d.datname IN ('planmate', 'planmate2')
UNION ALL
SELECT 'community 테이블 소유', count(*)::text
  FROM pg_tables WHERE schemaname='public' AND tableowner='community_app';

-- ── 되돌리기 ─────────────────────────────────────────────────────────────────
-- \connect community
-- DO $$ DECLARE o record; BEGIN
--   FOR o IN SELECT tablename FROM pg_tables WHERE schemaname='public' LOOP
--     EXECUTE format('ALTER TABLE public.%I OWNER TO postgres', o.tablename); END LOOP;
--   FOR o IN SELECT sequencename FROM pg_sequences WHERE schemaname='public' LOOP
--     EXECUTE format('ALTER SEQUENCE public.%I OWNER TO postgres', o.sequencename); END LOOP;
-- END $$;
-- \connect postgres
-- GRANT CONNECT ON DATABASE planmate2 TO PUBLIC;
-- GRANT CONNECT ON DATABASE planmate  TO PUBLIC;
-- DROP ROLE community_app;
