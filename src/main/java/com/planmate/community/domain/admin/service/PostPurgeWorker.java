package com.planmate.community.domain.admin.service;

import com.planmate.community.domain.admin.enums.ModerationTargetType;
import com.planmate.community.domain.admin.repository.ModerationRepository;
import com.planmate.community.domain.admin.repository.ModerationRepository.PurgeContent;
import com.planmate.community.domain.image.service.ImageService;
import com.planmate.community.domain.stats.service.UserStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 보관 기간이 지난 글 한 건을 DB 에서 지운다. 스케줄러가 글마다 호출하므로 트랜잭션 경계를 여기 둔다.
 */
@Component
@RequiredArgsConstructor
public class PostPurgeWorker {

    private final ModerationRepository repository;
    private final UserStatsService userStatsService;
    private final ImageService imageService;

    /**
     * 글과 그 자식 행을 지우고, 사라지는 댓글·좋아요만큼 작성자 통계를 되돌린다.
     * 반환값은 MinIO 에서 지울 이미지 URL 이다. 트랜잭션이 커밋된 뒤에 호출자가 지운다.
     */
    @Transactional
    public Set<String> purge(ModerationTargetType type, long postId) {
        Optional<PurgeContent> found = repository.findPurgeContent(type, postId);
        if (found.isEmpty()) {
            return Set.of();
        }
        PurgeContent content = found.get();

        Set<String> imageUrls = new HashSet<>(imageService.extractImageUrls(content.content()));
        if (content.thumbnailUrl() != null) {
            imageUrls.add(content.thumbnailUrl());
        }

        // 글 작성자의 글 수는 삭제 때 이미 줄었다. 댓글과 좋아요는 살아 있는 동안 세어졌으므로 여기서 되돌린다.
        repository.activeCommentCountsByAuthor(type, postId)
                .forEach((authorId, count) -> userStatsService.recordCommentsPurged(authorId, count.intValue()));
        long likes = repository.countLikes(type, postId);
        if (likes > 0) {
            userStatsService.recordLikeReceived(content.userId(), (int) -likes);
        }

        repository.deleteReports(type, postId);
        repository.deletePostRow(type, postId);
        return imageUrls;
    }
}
