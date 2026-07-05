package com.planmate.community.domain.reaction.controller;

import com.planmate.community.domain.reaction.dto.ReactionRequest;
import com.planmate.community.domain.reaction.dto.ReactionResponse;
import com.planmate.community.domain.reaction.service.ReactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Reaction", description = "게시글 좋아요/싫어요 API")
@RestController
@RequestMapping("/api/community/posts/{postId}/reaction")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    @Operation(summary = "반응 등록/토글", description = "좋아요 또는 싫어요를 등록합니다. 같은 타입을 다시 보내면 해제, 다른 타입이면 전환됩니다.")
    @PutMapping
    public ResponseEntity<ReactionResponse> react(
            Authentication authentication,
            @PathVariable("postId") Long postId,
            @Valid @RequestBody ReactionRequest request
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(reactionService.react(userId, postId, request.type()));
    }

    @Operation(summary = "반응 해제", description = "등록한 좋아요/싫어요를 해제합니다.")
    @DeleteMapping
    public ResponseEntity<ReactionResponse> cancelReaction(
            Authentication authentication,
            @PathVariable("postId") Long postId
    ) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(reactionService.cancelReaction(userId, postId));
    }
}
