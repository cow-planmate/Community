package com.planmate.community.domain.comment.entity;

import com.planmate.community.common.entity.BaseSoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.GenericGenerator;

import java.util.UUID;

/** 커뮤니티 댓글과 피드 댓글의 공통 매핑. 상속 이유는 {@link com.planmate.community.domain.post.entity.Post} 참고. */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public abstract class Comment extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "domain_comment_id")
    @GenericGenerator(name = "domain_comment_id", type = com.planmate.community.common.persistence.DomainSequenceGenerator.class)
    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "author_nickname", nullable = false, length = 100)
    private String authorNickname;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 대댓글 (1단계): 부모 댓글 ID, 최상위 댓글이면 null
    @Column(name = "parent_id")
    private Long parentId;

    public boolean isAuthor(UUID userId) {
        return this.userId.equals(userId);
    }

    protected void initialize(Long postId, UUID userId, String authorNickname, String content, Long parentId) {
        this.postId = postId;
        this.userId = userId;
        this.authorNickname = authorNickname;
        this.content = content;
        this.parentId = parentId;
    }

    public void updateContent(String content) {
        this.content = content;
    }
}
