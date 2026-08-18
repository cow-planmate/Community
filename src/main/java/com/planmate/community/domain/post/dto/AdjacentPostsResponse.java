package com.planmate.community.domain.post.dto;

import com.planmate.community.domain.post.entity.Post;

/**
 * 상세 화면 하단의 "이전 글 / 다음 글" 이동용 응답.
 *
 * 목록의 기본 정렬(최신순)을 그대로 따른다 — 이전 글은 목록에서 한 칸 위(더 최근에 올라온 글),
 * 다음 글은 한 칸 아래(더 오래된 글)다. 끝에 닿으면 해당 항목이 null 이다.
 */
public record AdjacentPostsResponse(
        PostLink prev,
        PostLink next
) {

    public record PostLink(Long id, String title) {
        static PostLink of(Post post) {
            return new PostLink(post.getPostId(), post.getTitle());
        }
    }

    public static AdjacentPostsResponse of(Post prev, Post next) {
        return new AdjacentPostsResponse(
                prev == null ? null : PostLink.of(prev),
                next == null ? null : PostLink.of(next)
        );
    }
}
