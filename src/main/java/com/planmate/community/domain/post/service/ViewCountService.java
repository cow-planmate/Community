package com.planmate.community.domain.post.service;

import com.planmate.community.domain.post.repository.PostRepository;
import com.planmate.community.domain.post.repository.FeedPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 조회수 증가 — 중복 제거 없이 조회 요청마다 1씩 증가한다.
 * 조회수는 Redis에 버퍼링 후 10초 주기로 DB 반영 (게시글별 INCR delta + dirty set).
 * Redis 장애 시 DB 직접 반영으로 fallback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewCountService {

    private static final String DELTA_KEY_PREFIX = "community:view:delta:";
    private static final String DIRTY_SET_KEY = "community:view:dirty";
    private static final String FEED_DELTA_KEY_PREFIX = "feed:view:delta:";
    private static final String FEED_DIRTY_SET_KEY = "feed:view:dirty";

    private final StringRedisTemplate redisTemplate;
    private final PostRepository postRepository;
    private final FeedPostRepository feedPostRepository;

    // Redis에 delta를 버퍼링하고, 실패 시 DB 직접 반영으로 fallback
    public void registerView(Long postId) {
        try {
            redisTemplate.opsForValue().increment(DELTA_KEY_PREFIX + postId);
            redisTemplate.opsForSet().add(DIRTY_SET_KEY, String.valueOf(postId));
        } catch (Exception e) {
            log.warn("조회수 버퍼링 실패, DB 직접 반영 (postId={}): {}", postId, e.getMessage());
            postRepository.addViewCount(postId, 1);
        }
    }

    public void registerFeedView(Long feedId) {
        try {
            redisTemplate.opsForValue().increment(FEED_DELTA_KEY_PREFIX + feedId);
            redisTemplate.opsForSet().add(FEED_DIRTY_SET_KEY, String.valueOf(feedId));
        } catch (Exception e) {
            log.warn("피드 조회수 버퍼링 실패, DB 직접 반영 (feedId={}): {}", feedId, e.getMessage());
            feedPostRepository.addViewCount(feedId, 1);
        }
    }

    // 버퍼링된 조회수 delta를 주기적으로 DB에 반영한다
    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void flushViewCounts() {
        flush(DIRTY_SET_KEY, DELTA_KEY_PREFIX, postRepository::addViewCount);
        flush(FEED_DIRTY_SET_KEY, FEED_DELTA_KEY_PREFIX, feedPostRepository::addViewCount);
    }

    private void flush(String dirtyKey, String deltaPrefix, ViewCountUpdater updater) {
        try {
            Set<String> dirtyPostIds = redisTemplate.opsForSet().members(dirtyKey);
            if (dirtyPostIds == null || dirtyPostIds.isEmpty()) {
                return;
            }
            redisTemplate.opsForSet().remove(dirtyKey, dirtyPostIds.toArray());

            for (String postIdValue : dirtyPostIds) {
                String deltaValue = redisTemplate.opsForValue().getAndDelete(deltaPrefix + postIdValue);
                if (deltaValue == null) {
                    continue;
                }
                try {
                    long delta = Long.parseLong(deltaValue);
                    if (delta > 0) {
                        updater.add(Long.parseLong(postIdValue), delta);
                    }
                } catch (Exception e) {
                    // GETDEL 이후 DB 반영 실패 — 조회수 특성상 유실 허용
                    log.warn("조회수 flush 실패 (postId={}, delta={}): {}", postIdValue, deltaValue, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("조회수 flush 중 Redis 오류: {}", e.getMessage());
        }
    }

    @FunctionalInterface
    private interface ViewCountUpdater {
        void add(Long id, long delta);
    }
}
