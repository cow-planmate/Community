package com.planmate.community.domain.post.service;

import com.planmate.community.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 조회수 증가 — Redis SETNX로 조회자별 24시간 중복 방지.
 * Redis 장애 시 fail-open (조회수를 세는 쪽으로 동작).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewCountService {

    private static final String VIEW_KEY_PREFIX = "community:view:";
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
            postRepository.incrementViewCount(postId);
        }
        return firstView;
    }
}
