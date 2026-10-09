package com.planmate.community.domain.admin.repository;

import com.planmate.community.domain.admin.dto.AdminDeletedPostResponse;
import com.planmate.community.domain.admin.dto.AdminReportItem;
import com.planmate.community.domain.admin.dto.AdminReportTargetResponse;
import com.planmate.community.domain.admin.enums.ModerationActionType;
import com.planmate.community.domain.admin.enums.ModerationTargetType;
import com.planmate.community.domain.report.enums.ReportStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 관리 화면용 조회와 갱신.
 *
 * <p>JPA 엔티티는 {@code @SQLRestriction("deleted_at IS NULL")} 로 삭제 글을 숨긴다. 보관 중인 삭제 글을
 * 다루는 쿼리는 전부 네이티브 SQL 로 쓴다. 테이블 이름은 {@link ModerationTargetType} 에서만 고른다.
 */
@Repository
@RequiredArgsConstructor
public class ModerationRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** 커뮤니티 글과 피드를 같은 모양으로 묶은 신고 요약. 신고가 한 건도 없는 글은 빠진다. */
    private static final String REPORT_TARGETS = """
            SELECT 'POST' AS target_type, r.post_id AS target_id, p.title, p.author_nickname,
                   p.user_id AS author_id, p.deleted_at,
                   COUNT(*) FILTER (WHERE r.status = :status) AS status_count,
                   COUNT(*) AS total_count,
                   MAX(r.created_at) AS latest_reported_at,
                   STRING_AGG(DISTINCT r.reason, ',') AS reasons
            FROM post_report r
            JOIN community_post p ON p.post_id = r.post_id
            GROUP BY r.post_id, p.title, p.author_nickname, p.user_id, p.deleted_at
            UNION ALL
            SELECT 'FEED', r.feed_id, p.title, p.author_nickname,
                   p.user_id, p.deleted_at,
                   COUNT(*) FILTER (WHERE r.status = :status),
                   COUNT(*),
                   MAX(r.created_at),
                   STRING_AGG(DISTINCT r.reason, ',')
            FROM post_report r
            JOIN feed_post p ON p.post_id = r.feed_id
            GROUP BY r.feed_id, p.title, p.author_nickname, p.user_id, p.deleted_at
            """;

    private static final String REPORT_TARGET_FILTER = """
            WHERE t.status_count > 0
              AND (CAST(:targetType AS VARCHAR) IS NULL OR t.target_type = :targetType)
            """;

    /** 보관 기간 안에 삭제된 글 (커뮤니티 글 + 피드). deleted_at 이 cutoff 이후여야 한다. */
    private static final String DELETED_POSTS = """
            SELECT 'POST' AS target_type, p.post_id AS target_id, p.title, p.author_nickname,
                   p.user_id AS author_id, p.deleted_at,
                   (SELECT COUNT(*) FROM post_report r WHERE r.post_id = p.post_id) AS report_count
            FROM community_post p
            WHERE p.deleted_at > :cutoff
            UNION ALL
            SELECT 'FEED', p.post_id, p.title, p.author_nickname,
                   p.user_id, p.deleted_at,
                   (SELECT COUNT(*) FROM post_report r WHERE r.feed_id = p.post_id)
            FROM feed_post p
            WHERE p.deleted_at > :cutoff
            """;

    private static final String DELETED_POST_FILTER = """
            WHERE CAST(:targetType AS VARCHAR) IS NULL OR t.target_type = :targetType
            """;

    // ── 신고 대상 목록·상세 ─────────────────────────────────────────────

    public List<AdminReportTargetResponse> findReportTargets(ReportStatus status, ModerationTargetType type,
                                                             int limit, long offset) {
        String sql = "SELECT * FROM (" + REPORT_TARGETS + ") t " + REPORT_TARGET_FILTER
                + " ORDER BY t.latest_reported_at DESC, t.target_id DESC LIMIT :limit OFFSET :offset";
        MapSqlParameterSource params = reportTargetParams(status, type)
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbc.query(sql, params, (rs, rowNum) -> new AdminReportTargetResponse(
                rs.getString("target_type"),
                rs.getLong("target_id"),
                rs.getString("title"),
                rs.getString("author_nickname"),
                rs.getObject("author_id", UUID.class),
                toDateTime(rs.getTimestamp("deleted_at")),
                rs.getLong("status_count"),
                rs.getLong("total_count"),
                toDateTime(rs.getTimestamp("latest_reported_at")),
                List.of(rs.getString("reasons").split(","))));
    }

    public long countReportTargets(ReportStatus status, ModerationTargetType type) {
        String sql = "SELECT COUNT(*) FROM (" + REPORT_TARGETS + ") t " + REPORT_TARGET_FILTER;
        return jdbc.queryForObject(sql, reportTargetParams(status, type), Long.class);
    }

    /** 삭제되어 보관 중인 글도 찾는다. 영구 삭제되었으면 비어 있다. */
    public Optional<ReportTargetContent> findTargetContent(ModerationTargetType type, long targetId) {
        List<ReportTargetContent> rows = jdbc.query("""
                SELECT title, content_text, user_id, author_nickname, category, created_at, deleted_at
                FROM %s WHERE post_id = :targetId
                """.formatted(type.getTable()), Map.of("targetId", targetId), (rs, rowNum) -> new ReportTargetContent(
                rs.getString("title"),
                rs.getString("content_text"),
                rs.getObject("user_id", UUID.class),
                rs.getString("author_nickname"),
                rs.getString("category"),
                toDateTime(rs.getTimestamp("created_at")),
                toDateTime(rs.getTimestamp("deleted_at"))));
        return rows.stream().findFirst();
    }

    public List<AdminReportItem> findReports(ModerationTargetType type, long targetId) {
        return jdbc.query("""
                SELECT report_id, reporter_id, reason, details, status, created_at, handled_by, handled_at
                FROM post_report WHERE %s = :targetId
                ORDER BY created_at DESC, report_id DESC
                """.formatted(type.getReportColumn()), Map.of("targetId", targetId), (rs, rowNum) -> new AdminReportItem(
                rs.getLong("report_id"),
                rs.getObject("reporter_id", UUID.class),
                rs.getString("reason"),
                rs.getString("details"),
                rs.getString("status"),
                toDateTime(rs.getTimestamp("created_at")),
                rs.getObject("handled_by", UUID.class),
                toDateTime(rs.getTimestamp("handled_at"))));
    }

    public boolean hasReports(ModerationTargetType type, long targetId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM post_report WHERE " + type.getReportColumn() + " = :targetId)",
                Map.of("targetId", targetId), Boolean.class));
    }

    /** 대기 중인 신고만 next 상태로 바꾼다. 바뀐 건수를 돌려준다. */
    public int markPendingReports(ModerationTargetType type, long targetId, ReportStatus next,
                                  UUID adminId, LocalDateTime handledAt) {
        return jdbc.update("""
                UPDATE post_report SET status = :next, handled_by = :adminId, handled_at = :handledAt
                WHERE %s = :targetId AND status = 'PENDING'
                """.formatted(type.getReportColumn()), new MapSqlParameterSource()
                .addValue("next", next.name())
                .addValue("adminId", adminId)
                .addValue("handledAt", handledAt)
                .addValue("targetId", targetId));
    }

    public void deleteReports(ModerationTargetType type, long targetId) {
        jdbc.update("DELETE FROM post_report WHERE " + type.getReportColumn() + " = :targetId",
                Map.of("targetId", targetId));
    }

    // ── 삭제·복구·영구 삭제 ───────────────────────────────────────────

    /** 삭제 여부와 작성자. 행 자체가 없으면(영구 삭제됨) 비어 있다. */
    public Optional<PostState> findPostState(ModerationTargetType type, long postId) {
        return jdbc.query("SELECT user_id, deleted_at FROM " + type.getTable() + " WHERE post_id = :postId",
                        Map.of("postId", postId),
                        (rs, rowNum) -> new PostState(
                                rs.getObject("user_id", UUID.class),
                                toDateTime(rs.getTimestamp("deleted_at"))))
                .stream().findFirst();
    }

    /** cutoff 이후에 삭제된 글만 되살린다. 되살린 건수를 돌려준다(0이면 이미 만료되었거나 삭제된 글이 아니다). */
    public int restorePost(ModerationTargetType type, long postId, LocalDateTime cutoff) {
        return jdbc.update("UPDATE " + type.getTable() + " SET deleted_at = NULL"
                        + " WHERE post_id = :postId AND deleted_at > :cutoff",
                new MapSqlParameterSource().addValue("postId", postId).addValue("cutoff", cutoff));
    }

    public List<AdminDeletedPostResponse> findDeletedPosts(ModerationTargetType type, LocalDateTime cutoff,
                                                           int retentionDays, int limit, long offset) {
        String sql = "SELECT * FROM (" + DELETED_POSTS + ") t " + DELETED_POST_FILTER
                + " ORDER BY t.deleted_at DESC, t.target_id DESC LIMIT :limit OFFSET :offset";
        MapSqlParameterSource params = deletedPostParams(type, cutoff)
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbc.query(sql, params, (rs, rowNum) -> {
            LocalDateTime deletedAt = toDateTime(rs.getTimestamp("deleted_at"));
            return new AdminDeletedPostResponse(
                    rs.getString("target_type"),
                    rs.getLong("target_id"),
                    rs.getString("title"),
                    rs.getString("author_nickname"),
                    rs.getObject("author_id", UUID.class),
                    deletedAt,
                    deletedAt.plusDays(retentionDays),
                    rs.getLong("report_count"));
        });
    }

    public long countDeletedPosts(ModerationTargetType type, LocalDateTime cutoff) {
        String sql = "SELECT COUNT(*) FROM (" + DELETED_POSTS + ") t " + DELETED_POST_FILTER;
        return jdbc.queryForObject(sql, deletedPostParams(type, cutoff), Long.class);
    }

    /** 보관 기간이 지난 글 id (영구 삭제 대상). */
    public List<Long> findExpiredPostIds(ModerationTargetType type, LocalDateTime cutoff, int limit) {
        return jdbc.queryForList("SELECT post_id FROM " + type.getTable()
                        + " WHERE deleted_at <= :cutoff ORDER BY post_id LIMIT :limit",
                new MapSqlParameterSource().addValue("cutoff", cutoff).addValue("limit", limit), Long.class);
    }

    public Optional<PurgeContent> findPurgeContent(ModerationTargetType type, long postId) {
        return jdbc.query("SELECT user_id, content, thumbnail_url FROM " + type.getTable()
                        + " WHERE post_id = :postId", Map.of("postId", postId),
                        (rs, rowNum) -> new PurgeContent(
                                rs.getObject("user_id", UUID.class),
                                rs.getString("content"),
                                rs.getString("thumbnail_url")))
                .stream().findFirst();
    }

    /** 영구 삭제로 사라질 댓글 중 아직 살아 있는 것을 작성자별로 센다 (통계 되돌리기용). */
    public Map<UUID, Long> activeCommentCountsByAuthor(ModerationTargetType type, long postId) {
        Map<UUID, Long> counts = new HashMap<>();
        jdbc.query("SELECT user_id, COUNT(*) AS cnt FROM " + type.getCommentTable()
                        + " WHERE post_id = :postId AND deleted_at IS NULL GROUP BY user_id",
                Map.of("postId", postId),
                (rs, rowNum) -> Map.entry(rs.getObject("user_id", UUID.class), rs.getLong("cnt")))
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }

    public long countLikes(ModerationTargetType type, long postId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + type.getReactionTable()
                        + " WHERE post_id = :postId AND type = 'LIKE'",
                Map.of("postId", postId), Long.class);
    }

    /**
     * 글 한 건을 DB 에서 지운다. 자식 행을 먼저 지워야 FK 에 걸리지 않는다.
     * 커뮤니티 글은 댓글·반응·모집 참여자를 직접 지우고, 피드는 V12 의 ON DELETE CASCADE 에 맡긴다.
     */
    public void deletePostRow(ModerationTargetType type, long postId) {
        Map<String, Long> params = Map.of("postId", postId);
        if (type == ModerationTargetType.POST) {
            jdbc.update("DELETE FROM community_comment WHERE post_id = :postId", params);
            jdbc.update("DELETE FROM community_reaction WHERE post_id = :postId", params);
            jdbc.update("DELETE FROM community_mate_participant WHERE post_id = :postId", params);
        }
        jdbc.update("DELETE FROM " + type.getTable() + " WHERE post_id = :postId", params);
    }

    // ── 감사 로그 ─────────────────────────────────────────────────────

    public void insertAdminAction(UUID adminId, ModerationActionType actionType, ModerationTargetType targetType,
                                  long targetId, String reason, LocalDateTime createdAt) {
        jdbc.update("""
                INSERT INTO community_admin_action_log (admin_user_id, action_type, target_type, target_id, reason, created_at)
                VALUES (:adminId, :actionType, :targetType, :targetId, :reason, :createdAt)
                """, new MapSqlParameterSource()
                .addValue("adminId", adminId)
                .addValue("actionType", actionType.name())
                .addValue("targetType", targetType.name())
                .addValue("targetId", targetId)
                .addValue("reason", reason, Types.VARCHAR)
                .addValue("createdAt", createdAt));
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────

    private static MapSqlParameterSource reportTargetParams(ReportStatus status, ModerationTargetType type) {
        return new MapSqlParameterSource()
                .addValue("status", status.name())
                .addValue("targetType", type == null ? null : type.name(), Types.VARCHAR);
    }

    private static MapSqlParameterSource deletedPostParams(ModerationTargetType type, LocalDateTime cutoff) {
        return new MapSqlParameterSource()
                .addValue("cutoff", cutoff)
                .addValue("targetType", type == null ? null : type.name(), Types.VARCHAR);
    }

    private static LocalDateTime toDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /** 삭제 여부와 작성자. */
    public record PostState(UUID userId, LocalDateTime deletedAt) {
        public boolean isDeleted() {
            return deletedAt != null;
        }
    }

    /** 영구 삭제 시 이미지 URL 과 통계 대상을 알아내는 데 필요한 값. */
    public record PurgeContent(UUID userId, String content, String thumbnailUrl) {
    }

    /** 신고 대상 원문. */
    public record ReportTargetContent(String title, String contentText, UUID authorId, String authorNickname,
                                      String category, LocalDateTime createdAt, LocalDateTime deletedAt) {
    }
}
