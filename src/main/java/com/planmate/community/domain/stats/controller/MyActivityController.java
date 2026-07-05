package com.planmate.community.domain.stats.controller;

import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.domain.comment.dto.CommentResponse;
import com.planmate.community.domain.post.dto.PostSummaryResponse;
import com.planmate.community.domain.stats.dto.MyStatsResponse;
import com.planmate.community.domain.stats.service.MyActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "MyActivity", description = "내 커뮤니티 활동 API")
@RestController
@RequestMapping("/api/community/me")
@RequiredArgsConstructor
public class MyActivityController {

    private final MyActivityService myActivityService;

    @Operation(summary = "내가 쓴 글", description = "내가 작성한 게시글을 최신순으로 조회합니다.")
    @GetMapping("/posts")
    public ResponseEntity<PageResponse<PostSummaryResponse>> getMyPosts(
            Authentication authentication,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(myActivityService.getMyPosts(userId, page, size));
    }

    @Operation(summary = "내가 좋아요한 글", description = "내가 좋아요를 누른 게시글을 최신순으로 조회합니다.")
    @GetMapping("/liked")
    public ResponseEntity<PageResponse<PostSummaryResponse>> getLikedPosts(
            Authentication authentication,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(myActivityService.getLikedPosts(userId, page, size));
    }

    @Operation(summary = "내가 쓴 댓글", description = "내가 작성한 댓글을 최신순으로 조회합니다.")
    @GetMapping("/comments")
    public ResponseEntity<PageResponse<CommentResponse>> getMyComments(
            Authentication authentication,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(myActivityService.getMyComments(userId, page, size));
    }

    @Operation(summary = "내 활동 통계", description = "게시글 수, 댓글 수, 레벨을 조회합니다.")
    @GetMapping("/stats")
    public ResponseEntity<MyStatsResponse> getMyStats(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(myActivityService.getMyStats(userId));
    }
}
