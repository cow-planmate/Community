-- Community 전용 DB 계정.
--
-- planmate 는 Postgres 인스턴스 하나(planmate-postgres)를 공유하되 **DB 와 계정을 서비스별로
-- 가른다.** 인스턴스를 나누지 않는 것은 이 규모에서 운영 비용이 이득보다 크기 때문이고,
-- 계정을 가르는 것은 한 서비스가 다른 서비스의 테이블을 읽을 수 없어야 하기 때문이다.
-- 이 파일이 없으면 docker-compose.community.yml 의 community_app 로 접속할 수 없다.
--
-- 실행:
--   docker exec -i planmate-postgres psql -U postgres \
--     -v pw="<COMMUNITY_DB_PASSWORD>" -f - < sql/community-db-account.sql
--
-- 멱등하다. 이미 있으면 건너뛰므로 여러 번 돌려도 된다.

\set ON_ERROR_STOP on

-- ── 1) 역할 ──────────────────────────────────────────────────────────────────
-- CREATE ROLE 은 IF NOT EXISTS 가 없어서 \gexec 로 조건부 실행한다.
SELECT format('CREATE ROLE community_app LOGIN PASSWORD %L', :'pw')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'community_app')
\gexec

-- 비밀번호를 바꿀 때도 이 스크립트를 그대로 쓴다.
SELECT format('ALTER ROLE community_app PASSWORD %L', :'pw')
\gexec

-- ── 2) 데이터베이스 ──────────────────────────────────────────────────────────
-- 소유자를 앱 계정으로 둔다. Flyway 가 이 계정으로 마이그레이션을 돌리기 때문이다.
SELECT 'CREATE DATABASE community OWNER community_app'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'community')
\gexec

-- ── 3) 교차 접속 차단 ────────────────────────────────────────────────────────
-- 기본값으로는 PUBLIC 에 CONNECT 가 있어서, 계정을 갈라도 서로의 DB 에 붙을 수 있다.
-- PUBLIC 에서 걷어내고 주인에게만 준다. postgres 는 슈퍼유저라 영향받지 않는다.
REVOKE CONNECT ON DATABASE community FROM PUBLIC;
GRANT  CONNECT ON DATABASE community TO community_app;

-- Backend-v2 의 DB. community_app 이 여기에 붙는 일은 없어야 한다.
REVOKE CONNECT ON DATABASE planmate2 FROM PUBLIC;

-- ── 4) 스키마 권한 ───────────────────────────────────────────────────────────
-- Postgres 15 부터 PUBLIC 은 schema public 에 CREATE 권한이 없다. 이걸 안 주면
-- Flyway 가 첫 마이그레이션에서 "permission denied for schema public" 으로 죽는다.
\connect community
ALTER SCHEMA public OWNER TO community_app;
GRANT ALL ON SCHEMA public TO community_app;
