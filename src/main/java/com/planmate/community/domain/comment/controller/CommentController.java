package com.planmate.community.domain.comment.controller;

import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.domain.comment.dto.CommentCreateRequest;
import com.planmate.community.domain.comment.dto.CommentResponse;
import com.planmate.community.domain.comment.dto.CommentUpdateRequest;
import com.planmate.community.domain.comment.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Comment", description = "커뮤니티 댓글 API")
@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "댓글 목록 조회", description = "게시글의 댓글을 작성순으로 페이징 조회합니다.")
    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<PageResponse<CommentResponse>> getComments(
            @PathVariable("postId") Long postId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(commentService.getComments(postId, page, size));
    }

    @Operation(summary = "댓글 작성", description = "게시글에 댓글을 작성합니다.")
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            Authentication authentication,
            @PathVariable("postId") Long postId,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        CommentResponse response = commentService.createComment(userId, postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "댓글 수정", description = "작성자가 댓글을 수정합니다.")
    @PatchMapping("/comments/{commentId}")
    public ResponseEntity<CommentResponse> updateComment(
            Authentication authentication,
            @PathVariable("commentId") Long commentId,
            @Valid @RequestBody CommentUpdateRequest request
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(commentService.updateComment(userId, commentId, request));
    }

    @Operation(summary = "댓글 삭제", description = "작성자 또는 관리자가 댓글을 삭제합니다 (soft delete).")
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            Authentication authentication,
            @PathVariable("commentId") Long commentId
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority()));
        commentService.deleteComment(userId, isAdmin, commentId);
        return ResponseEntity.noContent().build();
    }
}
