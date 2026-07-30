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

    @Operation(summary = "일정 가져가기", description = "피드 일정을 가져갑니다(포크). 횟수 제한이 없으며 가져갈 때마다 카운트가 증가합니다. "
            + "실제 여행 플랜 생성은 클라이언트가 Backend-v2의 POST /api/plan/full로 수행하고, 이 API는 성공 후 기록·카운트만 남깁니다.")
    @PostMapping("/fork")
    public ResponseEntity<ForkResponse> fork(
            Authentication authentication,
            @PathVariable("postId") Long postId
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(feedForkService.fork(userId, postId));
    }
}
