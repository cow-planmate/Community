package com.planmate.community.domain.comment.controller;

import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.domain.comment.dto.*;
import com.planmate.community.domain.comment.service.FeedCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedCommentController {
    private final FeedCommentService service;

    @GetMapping("/posts/{feedId}/comments")
    public ResponseEntity<PageResponse<CommentResponse>> get(@PathVariable Long feedId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(service.getComments(feedId, page, size));
    }
    @PostMapping("/posts/{feedId}/comments")
    public ResponseEntity<CommentResponse> create(Authentication auth, @PathVariable Long feedId,
            @Valid @RequestBody CommentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createComment(UUID.fromString(auth.getName()), feedId, request));
    }
    @PatchMapping("/comments/{commentId}")
    public ResponseEntity<CommentResponse> update(Authentication auth, @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request) {
        return ResponseEntity.ok(service.updateComment(UUID.fromString(auth.getName()), commentId, request));
    }
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable Long commentId) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        service.deleteComment(UUID.fromString(auth.getName()), admin, commentId);
        return ResponseEntity.noContent().build();
    }
}
