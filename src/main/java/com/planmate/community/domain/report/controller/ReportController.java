package com.planmate.community.domain.report.controller;

import com.planmate.community.domain.report.dto.ReportRequest;
import com.planmate.community.domain.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;

    @PostMapping("/api/community/posts/{postId}/reports")
    public ResponseEntity<Map<String, String>> reportCommunity(Authentication authentication, @PathVariable Long postId,
                                                 @Valid @RequestBody ReportRequest request) {
        reportService.report(UUID.fromString(authentication.getName()), postId, request, false);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "신고가 접수되었습니다."));
    }

    @PostMapping("/api/feed/posts/{postId}/reports")
    public ResponseEntity<Map<String, String>> reportFeed(Authentication authentication, @PathVariable Long postId,
                                           @Valid @RequestBody ReportRequest request) {
        reportService.report(UUID.fromString(authentication.getName()), postId, request, true);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "신고가 접수되었습니다."));
    }
}
