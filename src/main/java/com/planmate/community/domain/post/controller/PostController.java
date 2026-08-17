package com.planmate.community.domain.post.controller;

import com.planmate.community.common.dto.PageResponse;
import com.planmate.community.domain.post.dto.AdjacentPostsResponse;
import com.planmate.community.domain.post.dto.AnsweredRequest;
import com.planmate.community.domain.post.dto.PostCreateRequest;
import com.planmate.community.domain.post.dto.PostDetailResponse;
import com.planmate.community.domain.post.dto.PostSummaryResponse;
import com.planmate.community.domain.post.dto.PostUpdateRequest;
import com.planmate.community.domain.post.dto.RegionCountResponse;
import com.planmate.community.domain.post.service.PostService;
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

import java.util.List;
import java.util.UUID;

@Tag(name = "Post", description = "커뮤니티 게시글 API")
@RestController
@RequestMapping("/api/community/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "게시글 목록 조회", description = "게시판별 게시글 목록을 페이징으로 조회합니다. 검색어(q)와 정렬(latest|likes|views|forks)·정렬방향(order=asc|desc, 기본 desc)을 지원하며, 피드는 지역(region)·기간(minDays~maxDays)·태그(tag) 필터를 추가 지원합니다. userId를 주면 해당 사용자가 쓴 글만 조회합니다(프로필용, 다른 필터와 조합 불가). 해당 사용자가 프로필을 비공개로 두면 본인 외에는 403(USER_002)입니다.")
    @GetMapping
    public ResponseEntity<PageResponse<PostSummaryResponse>> getPosts(
            @RequestParam("category") String category,
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
            Authentication authentication
    ) {
        UUID viewerId = viewerId(authentication);
        return ResponseEntity.ok(postService.getPosts(category, page, size, sort, order, q, region, minDays, maxDays, tag, userId, viewerId));
    }

    @Operation(summary = "이전/다음 글 조회", description = "같은 게시판에서 이 글의 바로 앞(더 최근)·뒤(더 오래된) 글을 조회합니다. 끝에 닿으면 해당 항목은 null 입니다.")
    @GetMapping("/{postId}/adjacent")
    public ResponseEntity<AdjacentPostsResponse> getAdjacentPosts(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.getAdjacentPosts(postId));
    }

    @Operation(summary = "지역별 게시글 수 집계", description = "카테고리 내 지역(region)별 게시글 수를 집계합니다.")
    @GetMapping("/regions")
    public ResponseEntity<List<RegionCountResponse>> getRegionCounts(@RequestParam("category") String category) {
        return ResponseEntity.ok(postService.getRegionCounts(category));
    }

    @Operation(summary = "핫 게시글 조회", description = "게시판별 좋아요 상위 3개 게시글을 조회합니다.")
    @GetMapping("/hot")
    public ResponseEntity<List<PostSummaryResponse>> getHotPosts(@RequestParam("category") String category) {
        return ResponseEntity.ok(postService.getHotPosts(category));
    }

    @Operation(summary = "게시글 상세 조회", description = "게시글 상세 내용을 조회합니다. 조회수는 조회 요청마다 증가합니다.")
    @GetMapping("/{postId}")
    public ResponseEntity<PostDetailResponse> getPost(
            @PathVariable("postId") Long postId,
            Authentication authentication
    ) {
        UUID viewerId = viewerId(authentication);
        return ResponseEntity.ok(postService.getPost(postId, viewerId));
    }

    @Operation(summary = "게시글 작성", description = "게시판별 필드를 포함한 게시글을 작성합니다.")
    @PostMapping
    public ResponseEntity<PostDetailResponse> createPost(
            Authentication authentication,
            @Valid @RequestBody PostCreateRequest request
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        PostDetailResponse response = postService.createPost(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "게시글 수정", description = "작성자가 게시글을 수정합니다.")
    @PatchMapping("/{postId}")
    public ResponseEntity<PostDetailResponse> updatePost(
            Authentication authentication,
            @PathVariable("postId") Long postId,
            @Valid @RequestBody PostUpdateRequest request
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(postService.updatePost(userId, postId, request));
    }

    @Operation(summary = "QnA 답변 완료 표시", description = "작성자가 QnA 게시글의 답변 완료 여부를 변경합니다.")
    @PatchMapping("/{postId}/answered")
    public ResponseEntity<PostDetailResponse> updateAnswered(
            Authentication authentication,
            @PathVariable("postId") Long postId,
            @Valid @RequestBody AnsweredRequest request
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(postService.updateAnswered(userId, postId, request.isAnswered()));
    }

    @Operation(summary = "게시글 삭제", description = "작성자 또는 관리자가 게시글을 삭제합니다 (soft delete).")
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            Authentication authentication,
            @PathVariable("postId") Long postId
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority()));
        postService.deletePost(userId, isAdmin, postId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 비로그인도 허용되는 조회용 엔드포인트의 뷰어 id.
     * 토큰이 없으면 Spring이 AnonymousAuthenticationToken(getName()="anonymousUser")을 채워 넣으므로
     * null 검사만으로는 부족하다 — JwtAuthenticationFilter가 principal에 UUID를 넣는다는 점으로 판별한다.
     */
    private static UUID viewerId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof UUID principal ? principal : null;
    }
}
