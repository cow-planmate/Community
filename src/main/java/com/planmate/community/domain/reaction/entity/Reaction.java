package com.planmate.community.domain.reaction.entity;

import com.planmate.community.domain.reaction.enums.ReactionType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/** 커뮤니티 반응과 피드 반응의 공통 매핑. 상속 이유는 {@link com.planmate.community.domain.post.entity.Post} 참고. */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public abstract class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "domain_reaction_id")
    @GenericGenerator(name = "domain_reaction_id", type = com.planmate.community.common.persistence.DomainSequenceGenerator.class)
    @Column(name = "reaction_id")
    private Long reactionId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ReactionType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void changeType(ReactionType type) {
        this.type = type;
    }

    protected void initialize(Long postId, UUID userId, ReactionType type) {
        this.postId = postId;
        this.userId = userId;
        this.type = type;
    }

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
