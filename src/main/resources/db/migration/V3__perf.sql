-- VIEWS 정렬용 인덱스 (LATEST/LIKES/FORKS와 동일 패턴)
CREATE INDEX idx_community_post_category_view_count ON community_post (category, view_count DESC);
