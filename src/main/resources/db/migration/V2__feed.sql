-- V1의 인라인 CHECK 재생성 (FEED 카테고리 허용)
ALTER TABLE community_post DROP CONSTRAINT IF EXISTS community_post_category_check;
ALTER TABLE community_post ADD CONSTRAINT community_post_category_check
    CHECK (category IN ('FREE', 'QNA', 'MATE', 'RECOMMEND', 'FEED'));

-- FEED 전용 컬럼
ALTER TABLE community_post
    ADD COLUMN duration_days  INT,
    ADD COLUMN itinerary      JSONB,
    ADD COLUMN tags           JSONB,
    ADD COLUMN source_plan_id UUID,
    ADD COLUMN fork_count     INT NOT NULL DEFAULT 0;

-- 가져가기(포크) 기록
CREATE TABLE community_feed_fork (
    fork_id    BIGSERIAL PRIMARY KEY,
    post_id    BIGINT    NOT NULL REFERENCES community_post (post_id),
    user_id    UUID      NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_community_feed_fork_post_user UNIQUE (post_id, user_id)
);

-- 대댓글 (1단계)
ALTER TABLE community_comment
    ADD COLUMN parent_id BIGINT REFERENCES community_comment (comment_id);

-- 인덱스
CREATE INDEX idx_community_post_category_fork_count ON community_post (category, fork_count DESC);
CREATE INDEX idx_community_post_tags ON community_post USING GIN (tags);
CREATE INDEX idx_community_comment_parent ON community_comment (parent_id);
CREATE INDEX idx_community_feed_fork_user ON community_feed_fork (user_id);
