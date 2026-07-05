package com.planmate.community.domain.participant.repository;

import com.planmate.community.domain.participant.entity.MateParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MateParticipantRepository extends JpaRepository<MateParticipant, Long> {

    Optional<MateParticipant> findByPostIdAndUserId(Long postId, UUID userId);

    long countByPostId(Long postId);

    @Query("""
            SELECT p.postId AS postId, COUNT(p) AS participantCount
            FROM MateParticipant p
            WHERE p.postId IN :postIds
            GROUP BY p.postId
            """)
    List<ParticipantCount> countByPostIds(@Param("postIds") Collection<Long> postIds);

    interface ParticipantCount {
        Long getPostId();

        long getParticipantCount();
    }
}
