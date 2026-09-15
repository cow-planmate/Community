package com.planmate.community.domain.comment.repository;

import com.planmate.community.domain.comment.entity.FeedComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FeedCommentRepository extends JpaRepository<FeedComment, Long> {
    Page<FeedComment> findByPostIdOrderByCreatedAtAsc(Long postId, Pageable pageable);
    List<FeedComment> findByParentId(Long parentId);
    Page<FeedComment> findByUserIdOrderByCreatedAtDesc(java.util.UUID userId, Pageable pageable);

    // 탈퇴 시점에 옛 닉네임 스냅샷을 지운다 — PostRepository.scrubAuthorNickname 과 같은 이유
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE FeedComment c SET c.authorNickname = :nickname WHERE c.userId = :userId")
    void scrubAuthorNickname(@Param("userId") UUID userId, @Param("nickname") String nickname);
}
