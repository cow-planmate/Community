package com.planmate.community.common.client;

import build.buf.gen.planmate.internal.v1.InternalUser;
import build.buf.gen.planmate.internal.v1.InternalUserServiceGrpc;
import build.buf.gen.planmate.internal.v1.WatchUserChangesRequest;
import build.buf.gen.planmate.internal.v1.WatchUserChangesResponse;
import com.planmate.community.common.user.UserProjectionService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserChangeSubscriberTest {

    @Mock
    private UserProjectionService projectionService;

    private Server server;
    private ManagedChannel channel;
    private UserChangeSubscriber subscriber;

    private final CopyOnWriteArrayList<StreamObserver<WatchUserChangesResponse>> observers =
            new CopyOnWriteArrayList<>();
    private final AtomicInteger subscribeCount = new AtomicInteger();
    /** true면 구독 즉시 UNAVAILABLE로 끊어 재연결 동작을 확인한다. */
    private final AtomicBoolean failImmediately = new AtomicBoolean(false);
    /** 서버가 실제로 받은 커서 — 재개 지점을 제대로 실어 보내는지 확인한다. */
    private final AtomicInteger lastFromSequence = new AtomicInteger(-1);

    @BeforeEach
    void setUp() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new InternalUserServiceGrpc.InternalUserServiceImplBase() {
                    @Override
                    public void watchUserChanges(WatchUserChangesRequest request,
                                                 StreamObserver<WatchUserChangesResponse> observer) {
                        subscribeCount.incrementAndGet();
                        lastFromSequence.set((int) request.getFromSequence());
                        if (failImmediately.get()) {
                            observer.onError(Status.UNAVAILABLE.asRuntimeException());
                            return;
                        }
                        observers.add(observer);
                    }
                })
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();

        subscriber = new UserChangeSubscriber(InternalUserServiceGrpc.newStub(channel), projectionService);
    }

    @AfterEach
    void tearDown() {
        subscriber.shutdown();
        channel.shutdownNow();
        server.shutdownNow();
    }

    private static WatchUserChangesResponse change(UUID userId, long seq, String nickname) {
        return WatchUserChangesResponse.newBuilder()
                .setUserId(userId.toString())
                .setSequence(seq)
                .setUser(InternalUser.newBuilder()
                        .setUserId(userId.toString())
                        .setNickname(nickname)
                        .setProfilePublic(true))
                .build();
    }

    @Test
    @DisplayName("저장된 커서를 실어 보내 끊긴 지점부터 이어받는다")
    void resumesFromStoredCursor() {
        given(projectionService.resumeCursor()).willReturn(37L);

        subscriber.subscribe();

        await().atMost(2, TimeUnit.SECONDS).until(() -> lastFromSequence.get() >= 0);
        // 이 값이 0으로 새면 매 재연결마다 전체 스냅샷을 다시 받게 된다
        assertThat(lastFromSequence.get()).isEqualTo(37);
    }

    @Test
    @DisplayName("값이 실려 온 변경은 읽기 모델에 반영된다")
    void appliesChangeToProjection() {
        given(projectionService.isReady()).willReturn(true);
        subscriber.subscribe();
        await().atMost(2, TimeUnit.SECONDS).until(() -> !observers.isEmpty());

        UUID userId = UUID.randomUUID();
        StreamObserver<WatchUserChangesResponse> observer = observers.get(0);
        observer.onNext(change(userId, 8L, "바뀐닉"));
        observer.onCompleted();

        // 스냅샷이 끝난 뒤이므로 커서를 함께 전진시킨다
        verify(projectionService, timeout(2000)).applyBatch(anyList(), eq(true));
    }

    @Test
    @DisplayName("스냅샷 도중에는 커서를 올리지 않고, 완료 표시를 받은 시점에만 확정한다")
    void doesNotAdvanceCursorDuringSnapshot() {
        // 아직 초기 복제 전 — 서버가 스냅샷을 보내는 단계다
        given(projectionService.isReady()).willReturn(false);
        subscriber.subscribe();
        await().atMost(2, TimeUnit.SECONDS).until(() -> !observers.isEmpty());

        StreamObserver<WatchUserChangesResponse> observer = observers.get(0);
        observer.onNext(change(UUID.randomUUID(), 5L, "스냅닉"));
        observer.onNext(WatchUserChangesResponse.newBuilder()
                .setSequence(5L).setSnapshotComplete(true).build());

        // 스냅샷 항목은 커서를 올리지 않은 채로 반영돼야 한다.
        // 여기서 true 로 올리면, 완료 전에 죽었을 때 아직 못 받은 사용자를 영영 건너뛴다.
        verify(projectionService, timeout(2000)).applyBatch(anyList(), eq(false));
        verify(projectionService, never()).applyBatch(anyList(), eq(true));
        verify(projectionService, timeout(2000)).completeSnapshot(5L);
    }

    @Test
    @DisplayName("값이 없는 구버전 알림은 읽기 모델을 건드리지 않는다")
    void ignoresPayloadlessEventFromOldServer() {
        given(projectionService.isReady()).willReturn(true);
        subscriber.subscribe();
        await().atMost(2, TimeUnit.SECONDS).until(() -> !observers.isEmpty());

        StreamObserver<WatchUserChangesResponse> observer = observers.get(0);
        // 구버전 메인 백엔드는 ID만 보낸다 — 그 값으로 복제본을 채우면 닉네임이 지워진다
        observer.onNext(WatchUserChangesResponse.newBuilder()
                .setUserId(UUID.randomUUID().toString()).setSequence(3L).build());
        observer.onCompleted();

        verify(projectionService, never()).applyBatch(anyList(), anyBoolean());
    }

    @Test
    @DisplayName("스트림이 끊기면 재연결한다")
    void reconnectsAfterStreamError() {
        failImmediately.set(true);
        subscriber.subscribe();

        // 첫 구독 + 백오프(1초) 후 재구독
        await().atMost(5, TimeUnit.SECONDS).until(() -> subscribeCount.get() >= 2);
        assertThat(subscribeCount.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("구독 호출이 동기적으로 실패해도 재연결 루프가 끊기지 않는다")
    void reconnectsWhenSubscribeCallThrowsSynchronously() {
        // 채널이 이미 닫힌 상태 — 옵저버의 onError 가 아니라 호출 자체가 터질 수 있는 경로.
        // 여기서 재예약하지 않으면 구독이 영영 죽는다.
        channel.shutdownNow();

        subscriber.subscribe();

        // 예외가 밖으로 새지 않고, 재연결이 예약되어 다시 시도된다
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(subscriber.reconnectAttempts()).isPositive());
    }

    @Test
    @DisplayName("반영이 실패해도 커서를 올리지 않아 재연결 시 같은 구간을 다시 받는다")
    void survivesProjectionFailureWithoutAdvancingCursor() {
        given(projectionService.isReady()).willReturn(true);
        willThrow(new RuntimeException("db down"))
                .given(projectionService).applyBatch(anyList(), anyBoolean());

        subscriber.subscribe();
        await().atMost(2, TimeUnit.SECONDS).until(() -> !observers.isEmpty());

        StreamObserver<WatchUserChangesResponse> observer = observers.get(0);
        observer.onNext(change(UUID.randomUUID(), 1L, "하나"));
        observer.onCompleted();

        // 예외가 밖으로 새지 않았고, 커서를 전진시키지 않았으므로 유실이 아니다 —
        // 재연결하면 서버가 같은 구간부터 다시 재생해 준다.
        verify(projectionService, timeout(2000)).applyBatch(anyList(), anyBoolean());
        verify(projectionService, never()).completeSnapshot(anyLong());
    }
}
