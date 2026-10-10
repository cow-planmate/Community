package com.planmate.community.domain.admin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 삭제한 글을 되살릴 수 있는 기간. 기간이 지난 글은 {@link PostRetentionPurger} 가 영구 삭제한다.
 * deleted_at 이 cutoff 보다 뒤면 보관 중, 같거나 앞이면 만료다.
 */
@Component
public class PostRetentionPolicy {

    private final int days;

    public PostRetentionPolicy(@Value("${community.post.retention-days:30}") int days) {
        this.days = days;
    }

    public int days() {
        return days;
    }

    public LocalDateTime cutoff(LocalDateTime now) {
        return now.minusDays(days);
    }
}
