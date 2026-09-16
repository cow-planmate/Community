package com.planmate.community.domain.comment.repository;

import com.planmate.community.domain.comment.entity.CommunityComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<CommunityComment, Long> {

    Page<CommunityComment> findByPostIdOrderByCreatedAtAsc(Long postId, Pageable pageable);

    // 살아있는 대댓글 목록 (@SQLRestriction이 soft-deleted 자동 제외)
    List<CommunityComment> findByParentId(Long parentId);

    Page<CommunityComment> findByUserIdOrderByCreatedAtDesc(java.util.UUID userId, Pageable pageable);

    // 탈퇴 시점에 옛 닉네임 스냅샷을 지운다 — PostRepository.scrubAuthorNickname 과 같은 이유
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CommunityComment c SET c.authorNickname = :nickname WHERE c.userId = :userId")
    void scrubAuthorNickname(@Param("userId") UUID userId, @Param("nickname") String nickname);
}
