package com.planmate.community.common.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 메인 백엔드가 소유한 사용자 정보의 로컬 복제본.
 *
 * <p>이 서비스는 이 테이블을 <b>절대 직접 쓰지 않는다.</b> 유일한 기록 경로는
 * {@link ReplicatedUserRepository#upsert} 이고, 그 값의 출처는 메인 백엔드의 변경 스트림이다.
 * 그래서 변경자 메서드가 하나도 없다.
 *
 * <p>UserStats 와 마찬가지로 UUID 를 그대로 PK 로 쓰고 연관관계는 두지 않는다 —
 * 이 코드베이스에는 JPA 연관관계가 하나도 없고, 조인은 JPQL 서브쿼리로 직접 쓴다.
 */
@Getter
@Entity
@Table(name = "community_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReplicatedUser {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    /** 탈퇴 계정은 null. 화면에는 "탈퇴한 사용자"로 표시한다. */
    @Column(length = 100)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "avatar_hash", length = 32)
    private String avatarHash;

    @Column(name = "profile_public", nullable = false)
    private boolean profilePublic;

    @Column(nullable = false)
    private boolean deleted;

    /** 이 행에 반영된 변경 스트림의 시퀀스. 역행 갱신을 막는 데 쓴다. */
    @Column(name = "source_seq", nullable = false)
    private long sourceSeq;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
