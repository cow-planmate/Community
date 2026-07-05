package com.planmate.community.domain.participant.controller;

import com.planmate.community.domain.participant.dto.MateParticipationResponse;
import com.planmate.community.domain.participant.dto.MateStatusRequest;
import com.planmate.community.domain.participant.service.MateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Mate", description = "메이트 모집 참여 API")
@RestController
@RequestMapping("/api/community/posts/{postId}")
@RequiredArgsConstructor
public class MateController {

    private final MateService mateService;

    @Operation(summary = "메이트 참여", description = "메이트 모집에 참여합니다. 정원이 차면 자동으로 마감됩니다.")
    @PostMapping("/participants")
    public ResponseEntity<MateParticipationResponse> join(
            Authentication authentication,
            @PathVariable("postId") Long postId
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(mateService.join(userId, postId));
    }

    @Operation(summary = "메이트 참여 취소", description = "참여한 메이트 모집에서 나갑니다.")
    @DeleteMapping("/participants")
    public ResponseEntity<MateParticipationResponse> leave(
            Authentication authentication,
            @PathVariable("postId") Long postId
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(mateService.leave(userId, postId));
    }

    @Operation(summary = "모집 상태 변경", description = "작성자가 모집 상태(recruiting|closed)를 변경합니다.")
    @PatchMapping("/status")
    public ResponseEntity<MateParticipationResponse> changeStatus(
            Authentication authentication,
            @PathVariable("postId") Long postId,
            @Valid @RequestBody MateStatusRequest request
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(mateService.changeStatus(userId, postId, request.status()));
    }
}
