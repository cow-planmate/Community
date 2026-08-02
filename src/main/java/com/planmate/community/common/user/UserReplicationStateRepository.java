package com.planmate.community.common.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserReplicationStateRepository extends JpaRepository<UserReplicationState, Short> {

    /**
     * 커서 전진. 되돌아가지 않도록 GREATEST 로 잠근다 —
     * 늦게 도착한 배치가 커서를 뒤로 끌면 이미 반영한 구간을 다시 받게 된다(무해하지만 낭비).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE community_user_replication
               SET last_applied_seq = GREATEST(last_applied_seq, :seq),
                   updated_at = now()
             WHERE id = 1
            """, nativeQuery = true)
    void advanceCursor(@Param("seq") long seq);

    /**
     * 스냅샷 종료 확정. 커서를 올리는 것과 완료 표시를 <b>한 문장</b>에서 한다 —
     * 둘이 갈라지면 "커서는 올라갔는데 미완료"라는 복구 불가능한 상태가 생긴다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE community_user_replication
               SET last_applied_seq = GREATEST(last_applied_seq, :seq),
                   snapshot_complete = TRUE,
                   updated_at = now()
             WHERE id = 1
            """, nativeQuery = true)
    void completeSnapshot(@Param("seq") long seq);
}
