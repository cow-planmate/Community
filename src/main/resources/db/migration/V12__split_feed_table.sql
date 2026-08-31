-- 피드를 community_post의 상세 행이 아니라 독립 게시글 테이블로 분리한다.
-- 두 도메인은 FK도, ID 시퀀스도 공유하지 않는다. API 경로가 다르므로 같은 숫자 ID가 존재해도 된다.
CREATE SEQUENCE feed_post_id_seq;
CREATE SEQUENCE feed_comment_id_seq;
CREATE SEQUENCE feed_reaction_id_seq;

CREATE TABLE feed_post (
    post_id          BIGINT PRIMARY KEY DEFAULT nextval('feed_post_id_seq'),
    category         VARCHAR(16)  NOT NULL DEFAULT 'FEED' CHECK (category = 'FEED'),
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
    region           VARCHAR(100) NOT NULL,
    location         VARCHAR(255),
    lat              NUMERIC(10, 8),
    lng              NUMERIC(11, 8),
    duration_days    INT          NOT NULL CHECK (duration_days >= 1),
    itinerary        JSONB,
    tags             JSONB,
    source_plan_id   UUID,
    fork_count       INT          NOT NULL DEFAULT 0 CHECK (fork_count >= 0),
    -- @MappedSuperclass Post 에서 딸려온 커뮤니티 전용 필드. FEED에서는 항상 NULL이며 V13 에서 제거한다.
    is_answered      BOOLEAN,
    max_participants INT,
    status           VARCHAR(16),
    rating           NUMERIC(2, 1),
    place_address    VARCHAR(255),
    place_phone      VARCHAR(32),
    place_category   VARCHAR(255),
    place_url        VARCHAR(512),
    places           JSONB,
    deleted_at       TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL
);

INSERT INTO feed_post (
    post_id, category, user_id, author_nickname, title, content, content_text, thumbnail_url,
    like_count, dislike_count, comment_count, view_count, region, location, lat, lng,
    duration_days, itinerary, tags, source_plan_id, fork_count, deleted_at, created_at, updated_at
)
SELECT post_id, category, user_id, author_nickname, title, content, content_text, thumbnail_url,
       like_count, dislike_count, comment_count, view_count, region, location, lat, lng,
       duration_days, itinerary, tags, source_plan_id, fork_count, deleted_at, created_at, updated_at
FROM community_post
WHERE category = 'FEED';

CREATE TABLE feed_comment (
    comment_id      BIGINT PRIMARY KEY DEFAULT nextval('feed_comment_id_seq'),
    post_id         BIGINT       NOT NULL REFERENCES feed_post (post_id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL,
    author_nickname VARCHAR(100) NOT NULL,
    content         TEXT         NOT NULL,
    parent_id       BIGINT,
    deleted_at      TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);

INSERT INTO feed_comment
    (comment_id, post_id, user_id, author_nickname, content, parent_id, deleted_at, created_at, updated_at)
SELECT comment_id, post_id, user_id, author_nickname, content, parent_id, deleted_at, created_at, updated_at
FROM community_comment
WHERE post_id IN (SELECT post_id FROM feed_post);

ALTER TABLE feed_comment
    ADD CONSTRAINT fk_feed_comment_parent FOREIGN KEY (parent_id) REFERENCES feed_comment (comment_id);

CREATE TABLE feed_reaction (
    reaction_id BIGINT PRIMARY KEY DEFAULT nextval('feed_reaction_id_seq'),
    post_id     BIGINT     NOT NULL REFERENCES feed_post (post_id) ON DELETE CASCADE,
    user_id     UUID       NOT NULL,
    type        VARCHAR(8) NOT NULL CHECK (type IN ('LIKE', 'DISLIKE')),
    created_at  TIMESTAMP  NOT NULL,
    CONSTRAINT uq_feed_reaction_post_user UNIQUE (post_id, user_id)
);

INSERT INTO feed_reaction (reaction_id, post_id, user_id, type, created_at)
SELECT reaction_id, post_id, user_id, type, created_at
FROM community_reaction
WHERE post_id IN (SELECT post_id FROM feed_post);

DELETE FROM community_comment WHERE post_id IN (SELECT post_id FROM feed_post);
DELETE FROM community_reaction WHERE post_id IN (SELECT post_id FROM feed_post);

-- 피드 전용 fork의 부모 FK도 새 게시글 테이블로 옮긴다.
ALTER TABLE community_feed_fork DROP CONSTRAINT IF EXISTS community_feed_fork_post_id_fkey;
ALTER TABLE community_feed_fork RENAME TO feed_fork;
ALTER SEQUENCE community_feed_fork_fork_id_seq RENAME TO feed_fork_id_seq;

DELETE FROM community_post WHERE category = 'FEED';

ALTER TABLE feed_fork
    ADD CONSTRAINT fk_feed_fork_post FOREIGN KEY (post_id) REFERENCES feed_post (post_id) ON DELETE CASCADE;

DROP INDEX IF EXISTS idx_community_post_category_fork_count;
DROP INDEX IF EXISTS idx_community_post_tags;

-- 커뮤니티 테이블은 더 이상 FEED 를 담지 않는다 (V2 에서 넓혔던 범위를 되돌린다).
ALTER TABLE community_post DROP CONSTRAINT IF EXISTS community_post_category_check;
ALTER TABLE community_post ADD CONSTRAINT community_post_category_check
    CHECK (category IN ('FREE', 'QNA', 'MATE', 'RECOMMEND'));

ALTER TABLE community_post
    DROP CONSTRAINT IF EXISTS chk_post_feed_required,
    DROP CONSTRAINT IF EXISTS chk_post_feed_scope,
    DROP CONSTRAINT IF EXISTS chk_post_region_scope,
    DROP CONSTRAINT IF EXISTS chk_post_geo_scope,
    ADD CONSTRAINT chk_post_region_scope CHECK (category = 'MATE' OR region IS NULL),
    ADD CONSTRAINT chk_post_geo_scope
        CHECK (category = 'RECOMMEND' OR (location IS NULL AND lat IS NULL AND lng IS NULL)),
    DROP COLUMN duration_days,
    DROP COLUMN itinerary,
    DROP COLUMN tags,
    DROP COLUMN source_plan_id,
    DROP COLUMN fork_count;

CREATE INDEX idx_feed_post_created_at ON feed_post (created_at DESC);
CREATE INDEX idx_feed_post_user_created_at ON feed_post (user_id, created_at DESC);
CREATE INDEX idx_feed_post_region ON feed_post (region);
CREATE INDEX idx_feed_post_duration_days ON feed_post (duration_days);
CREATE INDEX idx_feed_post_fork_count ON feed_post (fork_count DESC);
CREATE INDEX idx_feed_post_tags ON feed_post USING GIN (tags);
CREATE INDEX idx_feed_post_title_trgm ON feed_post USING GIN (title gin_trgm_ops);
CREATE INDEX idx_feed_post_content_text_trgm ON feed_post USING GIN (content_text gin_trgm_ops);
CREATE INDEX idx_feed_comment_post_created_at ON feed_comment (post_id, created_at);
CREATE INDEX idx_feed_comment_user_created_at ON feed_comment (user_id, created_at DESC);
CREATE INDEX idx_feed_comment_parent ON feed_comment (parent_id);
CREATE INDEX idx_feed_reaction_user ON feed_reaction (user_id);

-- 백필한 기존 ID 다음부터 각 도메인이 독립적으로 발급한다.
SELECT setval('feed_post_id_seq', COALESCE((SELECT MAX(post_id) FROM feed_post), 0) + 1, false);
SELECT setval('feed_comment_id_seq', COALESCE((SELECT MAX(comment_id) FROM feed_comment), 0) + 1, false);
SELECT setval('feed_reaction_id_seq', COALESCE((SELECT MAX(reaction_id) FROM feed_reaction), 0) + 1, false);
