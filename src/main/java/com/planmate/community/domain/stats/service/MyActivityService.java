package com.planmate.community.domain.stats.service;

import com.planmate.community.common.client.UserClient;
import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.domain.comment.dto.CommentResponse;
import com.planmate.community.domain.comment.entity.Comment;
import com.planmate.community.domain.comment.repository.CommentRepository;
import com.planmate.community.domain.post.dto.PostSummaryResponse;
import com.planmate.community.domain.post.entity.Post;
import com.planmate.community.domain.post.repository.PostRepository;
import com.planmate.community.domain.post.service.PostAssembler;
import com.planmate.community.domain.stats.dto.MyStatsResponse;
import com.planmate.community.domain.stats.entity.UserStats;
import com.planmate.community.domain.stats.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyActivityService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserStatsRepository userStatsRepository;
    private final UserClient userClient;
    private final PostAssembler postAssembler;

    public PageResponse<PostSummaryResponse> getMyPosts(UUID userId, int page, int size) {
        Page<Post> posts = postRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable(page, size));
        return PageResponse.of(posts, postAssembler.toSummaries(posts.getContent()));
    }

    public PageResponse<PostSummaryResponse> getLikedPosts(UUID userId, int page, int size) {
        Page<Post> posts = postRepository.findLikedByUserId(userId, pageable(page, size));
        return PageResponse.of(posts, postAssembler.toSummaries(posts.getContent()));
    }

    public PageResponse<CommentResponse> getMyComments(UUID userId, int page, int size) {
        Page<Comment> comments = commentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable(page, size));

        String freshNickname = userClient.getNickname(userId).orElse(null);
        int level = userStatsRepository.findById(userId).map(UserStats::getLevel).orElse(1);

        return PageResponse.of(comments, comments.getContent().stream()
                .map(comment -> CommentResponse.of(comment, freshNickname, level))
                .toList());
    }

    public MyStatsResponse getMyStats(UUID userId) {
        return MyStatsResponse.of(userId, userStatsRepository.findById(userId).orElse(null));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
