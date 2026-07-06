package com.planmate.community.domain.fork.dto;

import com.planmate.community.domain.post.entity.Post;

public record ForkResponse(
        int forks,
        boolean myFork
) {

    public static ForkResponse of(Post post, boolean myFork) {
        return new ForkResponse(post.getForkCount(), myFork);
    }
}
