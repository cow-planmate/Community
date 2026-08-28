package com.planmate.community.domain.reaction.controller;

import com.planmate.community.domain.reaction.dto.*;
import com.planmate.community.domain.reaction.service.FeedReactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/feed/posts/{feedId}/reaction")
@RequiredArgsConstructor
public class FeedReactionController {
    private final FeedReactionService service;
    @PutMapping
    public ResponseEntity<ReactionResponse> react(Authentication auth, @PathVariable Long feedId,
            @Valid @RequestBody ReactionRequest request) {
        return ResponseEntity.ok(service.react(UUID.fromString(auth.getName()), feedId, request.type()));
    }
    @DeleteMapping
    public ResponseEntity<ReactionResponse> cancel(Authentication auth, @PathVariable Long feedId) {
        return ResponseEntity.ok(service.cancel(UUID.fromString(auth.getName()), feedId));
    }
}
