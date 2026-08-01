package com.planmate.community.common.client;

import build.buf.gen.planmate.internal.v1.InternalUserServiceGrpc;
import build.buf.gen.planmate.internal.v1.WatchUserChangesRequest;
import build.buf.gen.planmate.internal.v1.WatchUserChangesResponse;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 메인 백엔드의 사용자 프로필 변경 스트림을 구독해 이 서비스의 캐시를 무효화한다.
 *
 * 예전에는 메인 백엔드가 같은 Redis를 공유한다는 점을 이용해 커뮤니티 소유 키를 직접 지웠다.
 * 이제는 알림만 받고 <b>삭제는 여기서</b> 한다 — 그래야 두 서비스의 Redis를 분리할 수 있다.
 *
 * 스트림이 끊긴 동안에는 캐시 TTL(10분/1분)로 수렴하므로 정합성이 깨지지는 않고 반영만 늦어진다.
 * 따라서 재연결 실패는 경고만 남기고 서비스 기동/운영을 막지 않는다.
 */
@Slf4j
@Component
public class UserChangeSubscriber {

    private static final long INITIAL_BACKOFF_SECONDS = 1;
    private static final long MAX_BACKOFF_SECONDS = 60;
    // 이 시간 이상 붙어 있었으면 "정상 연결이었다"고 보고 백오프를 처음으로 되돌린다
    private static final long STABLE_CONNECTION_MILLIS = 60_000;

    private final InternalUserServiceGrpc.InternalUserServiceStub asyncStub;
    private final StringRedisTemplate redisTemplate;

    private final ScheduledExecutorService reconnector =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "user-change-subscriber");
                t.setDaemon(true);
                return t;
            });
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicLong subscribedAt = new AtomicLong();

    public UserChangeSubscriber(InternalUserServiceGrpc.InternalUserServiceStub internalUserAsyncStub,
                                StringRedisTemplate redisTemplate) {
        this.asyncStub = internalUserAsyncStub;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 컨텍스트가 완전히 뜬 뒤에 구독한다. Backend-v2가 아직 안 떠 있어도 백오프 재시도로 붙으므로
     * 기동 순서에 의존하지 않는다(Watchtower가 두 이미지를 따로 갱신한다).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void subscribe() {
        if (!running.get()) {
            return;
        }

        subscribedAt.set(System.currentTimeMillis());
        try {
            asyncStub.watchUserChanges(WatchUserChangesRequest.getDefaultInstance(),
                    new StreamObserver<>() {
                        @Override
                        public void onNext(WatchUserChangesResponse event) {
                            failures.set(0);
                            evict(event.getUserId());
                        }

                        @Override
                        public void onError(Throwable t) {
                            scheduleReconnect(t.getMessage());
                        }

                        @Override
                        public void onCompleted() {
                            // 서버가 정상 종료(재배포 등) — 다시 붙는다
                            scheduleReconnect("스트림이 서버에서 종료됨");
                        }
                    });
        } catch (Exception e) {
            // 호출 자체가 동기적으로 실패하면 옵저버의 onError 가 불리지 않는다.
            // 여기서 재예약하지 않으면 재연결 루프가 끊겨 영영 구독이 죽는다.
            scheduleReconnect("구독 요청 실패: " + e.getMessage());
        }
    }

    private void scheduleReconnect(String reason) {
        if (!running.get()) {
            return;
        }

        // 한동안 정상적으로 붙어 있었다면 이번 끊김은 새로운 장애다 — 1초부터 다시 시작한다.
        // 이게 없으면 재배포로 백오프가 한 번 올라간 뒤 영영 60초에 고정된다
        // (failures 는 이벤트가 실제로 도착할 때만 0이 되는데, 프로필 변경은 드물다).
        if (System.currentTimeMillis() - subscribedAt.get() >= STABLE_CONNECTION_MILLIS) {
            failures.set(0);
        }

        // 1s, 2s, 4s ... 최대 60s
        long delay = Math.min(MAX_BACKOFF_SECONDS,
                INITIAL_BACKOFF_SECONDS << Math.min(failures.getAndIncrement(), 6));
        log.warn("사용자 변경 스트림 끊김 ({}), {}초 후 재연결", reason, delay);

        try {
            reconnector.schedule(this::subscribe, delay, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 종료 중 executor가 이미 닫힌 경우 — 재연결을 포기해도 TTL로 수렴한다
            log.debug("재연결 예약 실패: {}", e.getMessage());
        }
    }

    private void evict(String userId) {
        try {
            redisTemplate.delete(List.of(
                    UserClient.CACHE_KEY_PREFIX + userId,
                    UserClient.VISIBILITY_CACHE_KEY_PREFIX + userId
            ));
        } catch (Exception e) {
            // 캐시 삭제 실패는 무시한다(best-effort) — TTL이 만료되면 최신 값으로 수렴한다
            log.warn("사용자 캐시 무효화 실패 (userId={}): {}", userId, e.getMessage());
        }
    }

    /** 재연결을 몇 번 예약했는지 — 테스트에서 재연결 루프가 살아있는지 확인하는 용도. */
    int reconnectAttempts() {
        return failures.get();
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        reconnector.shutdownNow();
    }
}
