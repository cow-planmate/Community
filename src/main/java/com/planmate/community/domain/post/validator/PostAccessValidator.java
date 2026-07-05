package com.planmate.community.domain.post.validator;

import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.post.entity.Post;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PostAccessValidator {

    public void validateAuthor(Post post, UUID userId) {
        if (!post.isAuthor(userId)) {
            throw new CommunityException(ErrorCode.POST_ACCESS_DENIED);
        }
    }

    public void validateAuthorOrAdmin(Post post, UUID userId, boolean isAdmin) {
        if (!isAdmin && !post.isAuthor(userId)) {
            throw new CommunityException(ErrorCode.POST_ACCESS_DENIED);
        }
    }
}
