CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 게시글 (단일 테이블 상속: category별 nullable 컬럼)
CREATE TABLE community_post (
    post_id          BIGSERIAL PRIMARY KEY,
    category         VARCHAR(16)  NOT NULL CHECK (category IN ('FREE', 'QNA', 'MATE', 'RECOMMEND')),
    user_id          UUID         NOT NULL,
    author_nickname  VARCHAR(100) NOT NULL,
    title            VARCHAR(255) NOT NULL,
    content          JSONB        NOT NULL,
    content_text     TEXT         NOT NULL DEFAULT '',
    thumbnail_url    VARCHAR(512),
    like_count       INT          NOT NULL DEFAULT 0,
    dislike_count    INT          NOT NULL DEFAULT 0,
    comment_count    INT          NOT NULL DEFAULT 0,
    view_count       INT          NOT NULL DEFAULT 0,
    -- QNA 전용
    is_answered      BOOLEAN,
    -- MATE 전용
    region           VARCHAR(100),
    max_participants INT,
    status           VARCHAR(16),
    -- RECOMMEND 전용
    location         VARCHAR(255),
    rating           NUMERIC(2, 1),
    lat              DOUBLE PRECISION,
    lng              DOUBLE PRECISION,
    deleted_at       TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL
);

-- 댓글
CREATE TABLE community_comment (
    comment_id      BIGSERIAL PRIMARY KEY,
    post_id         BIGINT       NOT NULL REFERENCES community_post (post_id),
    user_id         UUID         NOT NULL,
    author_nickname VARCHAR(100) NOT NULL,
    content         TEXT         NOT NULL,
    deleted_at      TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);

-- 좋아요/싫어요 (사용자당 게시글별 1개)
CREATE TABLE community_reaction (
    reaction_id BIGSERIAL PRIMARY KEY,
    post_id     BIGINT     NOT NULL REFERENCES community_post (post_id),
    user_id     UUID       NOT NULL,
    type        VARCHAR(8) NOT NULL CHECK (type IN ('LIKE', 'DISLIKE')),
    created_at  TIMESTAMP  NOT NULL,
    CONSTRAINT uq_community_reaction_post_user UNIQUE (post_id, user_id)
);

-- 메이트 참여자
CREATE TABLE community_mate_participant (
    participant_id BIGSERIAL PRIMARY KEY,
    post_id        BIGINT    NOT NULL REFERENCES community_post (post_id),
    user_id        UUID      NOT NULL,
    joined_at      TIMESTAMP NOT NULL,
    CONSTRAINT uq_community_mate_participant_post_user UNIQUE (post_id, user_id)
);

-- 사용자 활동 통계 (레벨 산정용)
CREATE TABLE community_user_stats (
    user_id       UUID PRIMARY KEY,
    post_count    INT       NOT NULL DEFAULT 0,
    comment_count INT       NOT NULL DEFAULT 0,
    level         INT       NOT NULL DEFAULT 1,
    updated_at    TIMESTAMP NOT NULL
);

-- 조회 인덱스
CREATE INDEX idx_community_post_category_created_at ON community_post (category, created_at DESC);
CREATE INDEX idx_community_post_category_like_count ON community_post (category, like_count DESC);
CREATE INDEX idx_community_post_user_created_at ON community_post (user_id, created_at DESC);
CREATE INDEX idx_community_comment_post_created_at ON community_comment (post_id, created_at);
CREATE INDEX idx_community_comment_user_created_at ON community_comment (user_id, created_at DESC);
CREATE INDEX idx_community_reaction_user ON community_reaction (user_id);

-- 검색 인덱스 (pg_trgm)
CREATE INDEX idx_community_post_title_trgm ON community_post USING GIN (title gin_trgm_ops);
CREATE INDEX idx_community_post_content_text_trgm ON community_post USING GIN (content_text gin_trgm_ops);
