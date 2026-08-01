package com.planmate.community.config;

import build.buf.gen.planmate.internal.v1.InternalUserServiceGrpc;
import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 메인 백엔드(Backend-v2)의 내부 gRPC 서비스에 붙는 채널/스텁.
 *
 * 평문(plaintext)으로 통신한다 — 컨테이너 간 내부 네트워크이며 외부에 노출되지 않는다.
 * 인증은 모든 호출에 x-internal-token 메타데이터를 붙이는 것으로 끝난다.
 */
@Configuration
public class InternalGrpcConfig {

    /** Backend-v2의 InternalTokenServerInterceptor가 검증하는 키와 같아야 한다. */
    private static final Metadata.Key<String> TOKEN_HEADER =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    /** application.yml의 grpc.client.internal-user.* 설정을 쓴다. */
    private static final String CHANNEL_NAME = "internal-user";

    @Bean
    public Channel internalUserChannel(GrpcChannelFactory channelFactory,
                                       @Value("${internal.api-token}") String internalApiToken) {
        Metadata headers = new Metadata();
        headers.put(TOKEN_HEADER, internalApiToken);
        ClientInterceptor tokenInterceptor = MetadataUtils.newAttachHeadersInterceptor(headers);

        return channelFactory.createChannel(CHANNEL_NAME, List.of(tokenInterceptor));
    }

    /** 캐시 미스 시의 단발성 조회용. deadline은 호출 시점에 건다(스텁에 미리 걸면 만료된 스텁이 된다). */
    @Bean
    public InternalUserServiceGrpc.InternalUserServiceBlockingStub internalUserBlockingStub(
            Channel internalUserChannel) {
        return InternalUserServiceGrpc.newBlockingStub(internalUserChannel);
    }

    /** 변경 알림 스트림 구독용 — 장기 연결이므로 deadline을 걸지 않는다. */
    @Bean
    public InternalUserServiceGrpc.InternalUserServiceStub internalUserAsyncStub(Channel internalUserChannel) {
        return InternalUserServiceGrpc.newStub(internalUserChannel);
    }
}
