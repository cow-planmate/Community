package com.planmate.community.domain.post.service;

import com.planmate.community.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Set;

/**
 * 조회수 증가 — Redis SETNX로 조회자별 24시간 중복 방지.
 * 조회수는 Redis에 버퍼링 후 10초 주기로 DB 반영 (게시글별 INCR delta + dirty set).
 * Redis 장애 시 fail-open (조회수를 세는 쪽으로 동작, DB 직접 반영으로 fallback).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewCountService {

    private static final String VIEW_KEY_PREFIX = "community:view:";
    private static final String DELTA_KEY_PREFIX = "community:view:delta:";
    private static final String DIRTY_SET_KEY = "community:view:dirty";
    private static final Duration DEDUPE_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final PostRepository postRepository;

    /**
     * @param viewerKey 로그인 사용자는 userId, 비로그인은 원격 IP
     * @return 조회수가 실제로 증가했는지
     */
    public boolean registerView(Long postId, String viewerKey) {
        boolean firstView;
        try {
            Boolean added = redisTemplate.opsForValue()
                    .setIfAbsent(VIEW_KEY_PREFIX + postId + ":" + viewerKey, "1", DEDUPE_TTL);
            firstView = Boolean.TRUE.equals(added);
        } catch (Exception e) {
            log.warn("조회수 중복방지 캐시 실패 (postId={}): {}", postId, e.getMessage());
            firstView = true;
        }

        if (firstView) {
            bufferView(postId);
        }
        return firstView;
    }

    // Redis에 delta를 버퍼링하고, 실패 시 DB 직접 반영으로 fallback
    private void bufferView(Long postId) {
        try {
            redisTemplate.opsForValue().increment(DELTA_KEY_PREFIX + postId);
            redisTemplate.opsForSet().add(DIRTY_SET_KEY, String.valueOf(postId));
        } catch (Exception e) {
            log.warn("조회수 버퍼링 실패, DB 직접 반영 (postId={}): {}", postId, e.getMessage());
            postRepository.addViewCount(postId, 1);
        }
    }

    // 버퍼링된 조회수 delta를 주기적으로 DB에 반영한다
    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void flushViewCounts() {
        try {
            Set<String> dirtyPostIds = redisTemplate.opsForSet().members(DIRTY_SET_KEY);
            if (dirtyPostIds == null || dirtyPostIds.isEmpty()) {
                return;
            }
            redisTemplate.opsForSet().remove(DIRTY_SET_KEY, dirtyPostIds.toArray());

            for (String postIdValue : dirtyPostIds) {
                String deltaValue = redisTemplate.opsForValue().getAndDelete(DELTA_KEY_PREFIX + postIdValue);
                if (deltaValue == null) {
                    continue;
                }
                try {
                    long delta = Long.parseLong(deltaValue);
                    if (delta > 0) {
                        postRepository.addViewCount(Long.parseLong(postIdValue), delta);
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
}
