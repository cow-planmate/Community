-- 마이페이지 "가져온 여행" — 가져간 시각 최신순 페이징용
CREATE INDEX idx_community_feed_fork_user_created_at ON community_feed_fork (user_id, created_at DESC);

-- 마이페이지 "작성한 여행기" — 사용자+게시판 필터용 (프로필의 다른 사용자 조회 포함)
CREATE INDEX idx_community_post_user_category_created_at ON community_post (user_id, category, created_at DESC);
