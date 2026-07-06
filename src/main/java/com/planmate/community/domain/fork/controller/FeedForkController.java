package com.planmate.community.domain.fork.controller;

import com.planmate.community.domain.fork.dto.ForkResponse;
import com.planmate.community.domain.fork.service.FeedForkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Fork", description = "피드 일정 가져가기 API")
@RestController
@RequestMapping("/api/community/posts/{postId}")
@RequiredArgsConstructor
public class FeedForkController {

    private final FeedForkService feedForkService;

    @Operation(summary = "일정 가져가기", description = "피드 일정을 가져갑니다(포크). 사용자당 게시글별 1회만 가능하며 취소할 수 없습니다.")
    @PostMapping("/fork")
    public ResponseEntity<ForkResponse> fork(
            Authentication authentication,
            @PathVariable("postId") Long postId
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(feedForkService.fork(userId, postId));
    }
}
