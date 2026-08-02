package com.planmate.community.common.client;

import build.buf.gen.planmate.internal.v1.InternalUserServiceGrpc;
import build.buf.gen.planmate.internal.v1.WatchUserChangesRequest;
import build.buf.gen.planmate.internal.v1.WatchUserChangesResponse;
import com.planmate.community.common.user.UserProjectionService;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 메인 백엔드의 사용자 변경 스트림을 구독해 로컬 읽기 모델을 따라잡는다.
 *
 * 예전에는 이 스트림이 "뭔가 바뀌었다" 알림이었고, 놓친 이벤트는 Redis 캐시 TTL로 수렴했다.
 * 이제는 복제 테이블을 채우므로 수렴시켜 줄 TTL이 없다 — 대신 커서를 들고 재연결해서
 * 빈 구간을 서버가 재생해 준다.
 *
 * 따라서 스트림이 끊겨도, 재배포로 서버가 재시작해도(Watchtower가 두 이미지를 따로 갱신하므로
 * 흔한 일이다) 유실이 없다. 반영만 늦어질 뿐이다.
 */
@Slf4j
@Component
public class UserChangeSubscriber {

    private static final long INITIAL_BACKOFF_SECONDS = 1;
    private static final long MAX_BACKOFF_SECONDS = 60;
    // 이 시간 이상 붙어 있었으면 "정상 연결이었다"고 보고 백오프를 처음으로 되돌린다
    private static final long STABLE_CONNECTION_MILLIS = 60_000;
    // 한 트랜잭션에 묶을 최대 건수. 크면 초기 백필의 트랜잭션 수가 줄고, 작으면 실패 시
    // 다시 받아야 하는 구간이 짧아진다.
    private static final int BATCH_SIZE = 200;

    private final InternalUserServiceGrpc.InternalUserServiceStub asyncStub;
    private final UserProjectionService projectionService;

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
                                UserProjectionService projectionService) {
        this.asyncStub = internalUserAsyncStub;
        this.projectionService = projectionService;
    }

    /**
     * 컨텍스트가 완전히 뜬 뒤에 구독한다. 메인 백엔드가 아직 안 떠 있어도 백오프 재시도로 붙으므로
     * 기동 순서에 의존하지 않는다(Watchtower가 두 이미지를 따로 갱신한다).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void subscribe() {
        if (!running.get()) {
            return;
        }

        subscribedAt.set(System.currentTimeMillis());
        try {
            // 커서를 실어 보낸다. 스냅샷이 아직 안 끝났으면 0 — 서버가 전체 스냅샷으로 응답한다.
            // 커서가 서버 보존 기간 밖이면 서버가 알아서 스냅샷으로 격하시킨다.
            long cursor = projectionService.resumeCursor();
            WatchUserChangesRequest request = WatchUserChangesRequest.newBuilder()
                    .setFromSequence(cursor)
                    .build();

            asyncStub.watchUserChanges(request, new ProjectionObserver());
        } catch (Exception e) {
            // 호출 자체가 동기적으로 실패하면 옵저버의 onError 가 불리지 않는다.
            // 여기서 재예약하지 않으면 재연결 루프가 끊겨 영영 구독이 죽는다.
            scheduleReconnect("구독 요청 실패: " + e.getMessage());
        }
    }

    /**
     * 스트림 콜백. DB 작업을 이 스레드에서 그대로 한다.
     *
     * 별도 큐로 넘기면 서버의 흐름 제어가 무력화된다. 서버는 이쪽이 받을 수 있을 때만 다음
     * 배치를 보내도록 설계돼 있는데, 무제한 큐를 두면 "받았다"고 거짓 신호를 주게 되고
     * 초기 백필에서 힙이 찬다.
     */
    private final class ProjectionObserver implements StreamObserver<WatchUserChangesResponse> {

        private final List<WatchUserChangesResponse> buffer = new ArrayList<>(BATCH_SIZE);
        /** 스냅샷 단계에서는 커서를 올리지 않는다 — completeSnapshot 이 확정한다. */
        private boolean snapshotPhase = !projectionService.isReady();

        @Override
        public void onNext(WatchUserChangesResponse event) {
            failures.set(0);

            if (event.getSnapshotComplete()) {
                flush();
                projectionService.completeSnapshot(event.getSequence());
                snapshotPhase = false;
                return;
            }

            if (!event.hasUser()) {
                // 구버전 서버라 값이 실려 오지 않는다. 복제본을 채울 수 없으므로 건너뛴다 —
                // 서버가 갱신되면 커서가 0인 채로 재연결해 전체 스냅샷부터 다시 시작한다.
                log.debug("값이 없는 변경 알림 — 구버전 메인 백엔드로 보인다 (userId={})", event.getUserId());
                return;
            }

            buffer.add(event);
            if (buffer.size() >= BATCH_SIZE) {
                flush();
            }
        }

        @Override
        public void onError(Throwable t) {
            // 반영하지 못한 버퍼는 버린다 — 커서를 올리지 않았으므로 재연결하면 다시 받는다
            buffer.clear();
            scheduleReconnect(t.getMessage());
        }

        @Override
        public void onCompleted() {
            // 서버가 정상 종료(재배포 등) — 남은 버퍼를 반영하고 다시 붙는다
            flush();
            scheduleReconnect("스트림이 서버에서 종료됨");
        }

        private void flush() {
            if (buffer.isEmpty()) {
                return;
            }
            try {
                projectionService.applyBatch(buffer, !snapshotPhase);
            } catch (Exception e) {
                // 커서를 올리지 않았으므로 재연결 시 같은 구간을 다시 받는다 — 유실이 아니다
                log.warn("사용자 읽기 모델 반영 실패 ({}건): {}", buffer.size(), e.getMessage());
            } finally {
                buffer.clear();
            }
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
            // 종료 중 executor가 이미 닫힌 경우 — 재연결을 포기해도 다음 기동에서 커서로 이어받는다
            log.debug("재연결 예약 실패: {}", e.getMessage());
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
