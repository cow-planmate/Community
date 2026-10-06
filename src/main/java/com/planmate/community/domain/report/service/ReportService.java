package com.planmate.community.domain.report.service;

import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import com.planmate.community.domain.post.repository.PostRepository;
import com.planmate.community.domain.post.repository.FeedPostRepository;
import com.planmate.community.domain.report.dto.ReportRequest;
import com.planmate.community.domain.report.enums.ReportReason;
import com.planmate.community.domain.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final PostRepository postRepository;
    private final FeedPostRepository feedPostRepository;
    private final ReportRepository reportRepository;

    @Transactional
    public void report(UUID reporterId, Long postId, ReportRequest request, boolean feed) {
        if (reporterId == null) throw new CommunityException(ErrorCode.UNAUTHORIZED);
        if (request == null || request.reason() == null) throw new CommunityException(ErrorCode.INVALID_INPUT);
        String details = request.details() == null ? "" : request.details().strip();
        if (details.length() > 1000 || (request.reason() == ReportReason.OTHER && details.isBlank())) {
            throw new CommunityException(ErrorCode.INVALID_INPUT, "기타 사유는 상세 내용을 입력해 주세요. (최대 1,000자)");
        }
        UUID authorId = feed
                ? feedPostRepository.findById(postId).orElseThrow(() -> new CommunityException(ErrorCode.POST_NOT_FOUND)).getUserId()
                : postRepository.findById(postId).orElseThrow(() -> new CommunityException(ErrorCode.POST_NOT_FOUND)).getUserId();
        if (reporterId.equals(authorId)) throw new CommunityException(ErrorCode.REPORT_SELF_DENIED);
        if (!reportRepository.insert(postId, reporterId, request.reason().name(), details, feed)) {
            throw new CommunityException(ErrorCode.REPORT_ALREADY_SUBMITTED);
        }
    }
}
