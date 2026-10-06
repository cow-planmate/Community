CREATE TABLE post_report (
    report_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id BIGINT REFERENCES community_post (post_id),
    feed_id BIGINT REFERENCES feed_post (post_id),
    reporter_id UUID NOT NULL,
    reason VARCHAR(32) NOT NULL CHECK (reason IN ('SPAM', 'ABUSE', 'INAPPROPRIATE', 'PRIVACY', 'OTHER')),
    details VARCHAR(1000) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((post_id IS NOT NULL) <> (feed_id IS NOT NULL)),
    UNIQUE (post_id, reporter_id),
    UNIQUE (feed_id, reporter_id)
);
CREATE INDEX idx_post_report_created_at ON post_report (created_at DESC);
