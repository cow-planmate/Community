package com.planmate.community.domain.report.repository;

import com.planmate.community.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@Import(ReportRepository.class)
class ReportRepositoryTest extends PostgresTestBase {
    @Autowired ReportRepository reports;
    @Autowired JdbcTemplate jdbc;

    @Test void sameNumberInBothDomainsCanBeReportedOncePerReporter() {
        UUID author = UUID.randomUUID();
        UUID reporter = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO community_post (post_id, category, user_id, author_nickname, title,
                    content, content_text, created_at, updated_at)
                VALUES (90001, 'FREE', ?, '작성자', '글', '[]'::jsonb, '', now(), now())
                """, author);
        jdbc.update("""
                INSERT INTO feed_post (post_id, category, user_id, author_nickname, title,
                    content, content_text, region, duration_days, created_at, updated_at)
                VALUES (90001, 'FEED', ?, '작성자', '여행기', '[]'::jsonb, '', '서울', 1, now(), now())
                """, author);
        assertThat(reports.insert(90001L, reporter, "SPAM", "광고", false)).isTrue();
        assertThat(reports.insert(90001L, reporter, "ABUSE", "중복", false)).isFalse();
        assertThat(reports.insert(90001L, reporter, "PRIVACY", "개인정보", true)).isTrue();
        assertThat(reports.insert(90001L, reporter, "SPAM", "중복", true)).isFalse();
        assertThat(reports.insert(90001L, UUID.randomUUID(), "OTHER", "사유", false)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_report", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT details FROM post_report WHERE reporter_id = ? AND post_id = 90001", String.class, reporter)).isEqualTo("광고");
    }
}
