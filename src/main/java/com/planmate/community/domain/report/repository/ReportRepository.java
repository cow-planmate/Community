package com.planmate.community.domain.report.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReportRepository {
    private final JdbcTemplate jdbcTemplate;

    public boolean insert(Long postId, UUID reporterId, String reason, String details, boolean feed) {
        return jdbcTemplate.update("""
                INSERT INTO post_report (post_id, feed_id, reporter_id, reason, details)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, feed ? null : postId, feed ? postId : null, reporterId, reason, details) == 1;
    }
}
