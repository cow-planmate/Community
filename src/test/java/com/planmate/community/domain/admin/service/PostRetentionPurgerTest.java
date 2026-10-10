package com.planmate.community.domain.admin.service;

import com.planmate.community.domain.admin.enums.ModerationTargetType;
import com.planmate.community.domain.admin.repository.ModerationRepository;
import com.planmate.community.domain.image.service.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostRetentionPurgerTest {

    @Mock ModerationRepository repository;
    @Mock PostPurgeWorker purgeWorker;
    @Mock ImageService imageService;

    PostRetentionPurger purger;

    @BeforeEach
    void setUp() {
        purger = new PostRetentionPurger(repository, purgeWorker, imageService, new PostRetentionPolicy(30));
    }

    @Test void purgesExpiredPostsOfBothKindsAndDeletesTheirImages() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 9, 4, 30);
        LocalDateTime cutoff = now.minusDays(30);
        when(repository.findExpiredPostIds(ModerationTargetType.POST, cutoff, 100)).thenReturn(List.of(1L));
        when(repository.findExpiredPostIds(ModerationTargetType.FEED, cutoff, 100)).thenReturn(List.of(2L));
        when(purgeWorker.purge(ModerationTargetType.POST, 1L)).thenReturn(Set.of("http://img/a.png"));
        when(purgeWorker.purge(ModerationTargetType.FEED, 2L)).thenReturn(Set.of());

        assertThat(purger.purgeExpired(now)).isEqualTo(2);

        verify(imageService).deleteAll(Set.of("http://img/a.png"));
        verify(imageService).deleteAll(Set.of());
    }

    @Test void aFailedPostIsSkippedWithoutDeletingImagesAndDoesNotStopTheBatch() {
        when(repository.findExpiredPostIds(eq(ModerationTargetType.POST), any(), eq(100))).thenReturn(List.of(1L, 2L));
        when(repository.findExpiredPostIds(eq(ModerationTargetType.FEED), any(), eq(100))).thenReturn(List.of());
        when(purgeWorker.purge(ModerationTargetType.POST, 1L)).thenThrow(new IllegalStateException("db down"));
        when(purgeWorker.purge(ModerationTargetType.POST, 2L)).thenReturn(Set.of("http://img/b.png"));

        assertThat(purger.purgeExpired(LocalDateTime.now())).isEqualTo(1);

        verify(imageService).deleteAll(Set.of("http://img/b.png"));
        verify(imageService, times(1)).deleteAll(anyCollection());
    }
}
