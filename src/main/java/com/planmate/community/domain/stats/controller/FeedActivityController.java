package com.planmate.community.domain.stats.controller;

import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.domain.comment.dto.CommentResponse;
import com.planmate.community.domain.post.dto.PostSummaryResponse;
import com.planmate.community.domain.stats.service.FeedActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedActivityController {
    private final FeedActivityService service;

    @GetMapping("/me/posts")
    public ResponseEntity<PageResponse<PostSummaryResponse>> myPosts(Authentication auth, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        UUID id = UUID.fromString(auth.getName()); return ResponseEntity.ok(service.posts(id, id, page, size));
    }
    @GetMapping("/me/liked")
    public ResponseEntity<PageResponse<PostSummaryResponse>> liked(Authentication auth, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok(service.liked(UUID.fromString(auth.getName()), page, size));
    }
    @GetMapping("/me/comments")
    public ResponseEntity<PageResponse<CommentResponse>> comments(Authentication auth, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok(service.comments(UUID.fromString(auth.getName()), page, size));
    }
    @GetMapping("/users/{userId}/comments")
    public ResponseEntity<PageResponse<CommentResponse>> userComments(@PathVariable UUID userId, Authentication auth, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        UUID viewer = auth != null && auth.getPrincipal() instanceof UUID id ? id : null;
        return ResponseEntity.ok(service.comments(userId, viewer, page, size));
    }
    @GetMapping("/users/{userId}/posts")
    public ResponseEntity<PageResponse<PostSummaryResponse>> userPosts(@PathVariable UUID userId, Authentication auth, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        UUID viewer = auth != null && auth.getPrincipal() instanceof UUID id ? id : null;
        return ResponseEntity.ok(service.posts(userId, viewer, page, size));
    }
}
