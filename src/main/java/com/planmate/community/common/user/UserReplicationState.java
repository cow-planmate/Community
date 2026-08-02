package com.planmate.community.common.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 복제 커서. 단일 행(id = 1) 고정이다.
 *
 * <p>{@code snapshotComplete} 가 false 인 동안에는 복제본이 불완전하므로, 이 값을 보고
 * 신규 필터를 끄고 공개 여부 판정도 원격 조회로 되돌려야 한다. 즉 백필이 끝나기 전의
 * 동작은 이 기능이 들어오기 전과 완전히 같다.
 */
@Getter
@Entity
@Table(name = "community_user_replication")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserReplicationState {

    /** 단일 행 강제 — 스키마의 CHECK (id = 1) 와 짝을 이룬다. */
    public static final short SINGLETON_ID = 1;

    @Id
    private short id;

    @Column(name = "last_applied_seq", nullable = false)
    private long lastAppliedSeq;

    @Column(name = "snapshot_complete", nullable = false)
    private boolean snapshotComplete;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
