package com.planmate.community.common.client;

import build.buf.gen.planmate.internal.v1.InternalUserServiceGrpc;
import build.buf.gen.planmate.internal.v1.WatchUserChangesRequest;
import build.buf.gen.planmate.internal.v1.WatchUserChangesResponse;
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
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserChangeSubscriberTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private Server server;
    private ManagedChannel channel;
    private UserChangeSubscriber subscriber;

    private final CopyOnWriteArrayList<StreamObserver<WatchUserChangesResponse>> observers =
            new CopyOnWriteArrayList<>();
    private final AtomicInteger subscribeCount = new AtomicInteger();
    /** true면 구독 즉시 UNAVAILABLE로 끊어 재연결 동작을 확인한다. */
    private final AtomicBoolean failImmediately = new AtomicBoolean(false);

    @BeforeEach
    void setUp() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new InternalUserServiceGrpc.InternalUserServiceImplBase() {
                    @Override
                    public void watchUserChanges(WatchUserChangesRequest request,
                                                 StreamObserver<WatchUserChangesResponse> observer) {
                        subscribeCount.incrementAndGet();
                        if (failImmediately.get()) {
                            observer.onError(Status.UNAVAILABLE.asRuntimeException());
                            return;
                        }
                        observers.add(observer);
                    }
                })
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();

        subscriber = new UserChangeSubscriber(InternalUserServiceGrpc.newStub(channel), redisTemplate);
    }

    @AfterEach
    void tearDown() {
        subscriber.shutdown();
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    @DisplayName("변경 이벤트를 받으면 프로필·공개여부 캐시 키를 지운다")
    void evictsBothCacheKeysOnEvent() {
        subscriber.subscribe();
        await().atMost(2, TimeUnit.SECONDS).until(() -> !observers.isEmpty());

        UUID userId = UUID.randomUUID();
        observers.get(0).onNext(WatchUserChangesResponse.newBuilder()
                .setUserId(userId.toString()).build());

        verify(redisTemplate, timeout(2000)).delete(List.of(
                UserClient.CACHE_KEY_PREFIX + userId,
                UserClient.VISIBILITY_CACHE_KEY_PREFIX + userId));
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
    @DisplayName("캐시 삭제가 실패해도 스트림은 계속 동작한다(best-effort)")
    void survivesRedisFailure() {
        willThrow(new RuntimeException("redis down")).given(redisTemplate).delete(anyCollection());

        subscriber.subscribe();
        await().atMost(2, TimeUnit.SECONDS).until(() -> !observers.isEmpty());

        StreamObserver<WatchUserChangesResponse> observer = observers.get(0);
        observer.onNext(WatchUserChangesResponse.newBuilder()
                .setUserId(UUID.randomUUID().toString()).build());

        // 예외가 새어나가 스트림이 끊기지 않았다면 두 번째 이벤트도 처리된다
        observer.onNext(WatchUserChangesResponse.newBuilder()
                .setUserId(UUID.randomUUID().toString()).build());

        verify(redisTemplate, timeout(2000).times(2)).delete(anyCollection());
    }
}
