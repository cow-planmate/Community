package com.planmate.community.domain.admin.service;

import com.planmate.community.domain.admin.enums.ModerationTargetType;
import com.planmate.community.domain.admin.repository.ModerationRepository;
import com.planmate.community.domain.image.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

/** 보관 기간이 지난 삭제 글을 매일 영구 삭제한다. 실패한 글은 다음 실행에서 다시 시도한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostRetentionPurger {

    private static final int BATCH_SIZE = 100;

    private final ModerationRepository repository;
    private final PostPurgeWorker purgeWorker;
    private final ImageService imageService;
    private final PostRetentionPolicy retentionPolicy;

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void purgeExpiredPosts() {
        purgeExpired(LocalDateTime.now());
    }

    /** 처리한 글 수를 돌려준다. 시각을 인자로 받아 테스트에서 보관 기간 경계를 고정할 수 있다. */
    public int purgeExpired(LocalDateTime now) {
        LocalDateTime cutoff = retentionPolicy.cutoff(now);
        int purged = 0;
        for (ModerationTargetType type : ModerationTargetType.values()) {
            for (long postId : repository.findExpiredPostIds(type, cutoff, BATCH_SIZE)) {
                Set<String> imageUrls;
                try {
                    imageUrls = purgeWorker.purge(type, postId);
                } catch (RuntimeException e) {
                    log.warn("보관 기간 만료 글 영구 삭제 실패, 다음 실행에서 재시도: type={}, postId={}", type, postId, e);
                    continue;
                }
                purged++;
                // 커밋 뒤에 지운다. 이미지 삭제는 best-effort 라 실패해도 DB 결과는 그대로 둔다.
                imageService.deleteAll(imageUrls);
            }
        }
        return purged;
    }
}
