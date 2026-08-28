package com.planmate.community.domain.post.controller;

import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.domain.post.dto.*;
import com.planmate.community.domain.post.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/feed/posts")
@RequiredArgsConstructor
public class FeedPostController {

    private final PostService postService;

    @GetMapping
    public ResponseEntity<PageResponse<PostSummaryResponse>> getPosts(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "latest") String sort,
            @RequestParam(value = "order", defaultValue = "desc") String order,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "region", required = false) String region,
            @RequestParam(value = "minDays", required = false) Integer minDays,
            @RequestParam(value = "maxDays", required = false) Integer maxDays,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "userId", required = false) UUID userId,
            Authentication authentication) {
        return ResponseEntity.ok(postService.getPosts("feed", page, size, sort, order, q, region,
                minDays, maxDays, tag, userId, viewerId(authentication)));
    }

    @GetMapping("/regions")
    public ResponseEntity<List<RegionCountResponse>> getRegions() {
        return ResponseEntity.ok(postService.getRegionCounts("feed"));
    }

    @GetMapping("/hot")
    public ResponseEntity<List<PostSummaryResponse>> getHotPosts() {
        return ResponseEntity.ok(postService.getHotPosts("feed"));
    }

    @GetMapping("/{feedId}")
    public ResponseEntity<PostDetailResponse> getPost(@PathVariable Long feedId, Authentication authentication) {
        return ResponseEntity.ok(postService.getFeedPost(feedId, viewerId(authentication)));
    }

    @GetMapping("/{feedId}/adjacent")
    public ResponseEntity<AdjacentPostsResponse> getAdjacent(@PathVariable Long feedId) {
        return ResponseEntity.ok(postService.getAdjacentFeedPosts(feedId));
    }

    @PostMapping
    public ResponseEntity<PostDetailResponse> createPost(Authentication authentication,
                                                          @Valid @RequestBody PostCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postService.createFeedPost(UUID.fromString(authentication.getName()), request));
    }

    @PatchMapping("/{feedId}")
    public ResponseEntity<PostDetailResponse> updatePost(Authentication authentication, @PathVariable Long feedId,
                                                          @Valid @RequestBody PostUpdateRequest request) {
        return ResponseEntity.ok(postService.updateFeedPost(UUID.fromString(authentication.getName()), feedId, request));
    }

    @DeleteMapping("/{feedId}")
    public ResponseEntity<Void> deletePost(Authentication authentication, @PathVariable Long feedId) {
        UUID userId = UUID.fromString(authentication.getName());
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        postService.deleteFeedPost(userId, isAdmin, feedId);
        return ResponseEntity.noContent().build();
    }

    private static UUID viewerId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof UUID principal ? principal : null;
    }
}
