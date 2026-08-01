package com.planmate.community.security;

import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.security.Key;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 메인 백엔드의 토큰 서명 공개키를 JWKS 에서 받아 들고 있는다.
 *
 * <p>지금까지 이 서비스는 메인 백엔드와 <b>같은 HS256 대칭키</b>를 나눠 가졌다. 검증만
 * 하면 되는 쪽이 서명까지 할 수 있는 키를 들고 있었다는 뜻이고, 그 상태로는 이 서비스가
 * 침해당하면 메인 백엔드의 토큰을 위조할 수 있다. 공개키만 받아 오면 그 경로가 사라진다.
 *
 * <p>기동 시점에 받아오지 않는다 — 메인 백엔드가 아직 안 떠 있어도 이 서비스는 떠야 한다.
 * 첫 검증 때 가져오고, 모르는 kid 가 오면(키 교체) 다시 가져온다.
 */
@Slf4j
@Component
public class JwksKeyProvider {

    /** 키 교체 직후 몰려드는 요청이 전부 fetch 를 유발하지 않도록 최소 간격을 둔다. */
    private static final Duration MIN_REFETCH_INTERVAL = Duration.ofSeconds(30);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;
    private final String jwksUri;

    private final AtomicReference<Map<String, PublicKey>> keys = new AtomicReference<>(Map.of());
    private final AtomicReference<Instant> lastFetch = new AtomicReference<>(Instant.EPOCH);

    public JwksKeyProvider(RestClient.Builder builder,
                           @Value("${jwt.jwks-uri}") String jwksUri) {
        this.jwksUri = jwksUri;
        this.restClient = builder.build();
    }

    /**
     * kid 에 해당하는 공개키. 캐시에 없으면 한 번 다시 받아본다.
     * 끝내 못 찾으면 null 을 돌려주고, 호출부는 그 토큰을 거부해야 한다 —
     * 검증할 수 없는 토큰을 통과시키면 안 된다.
     */
    public Key find(String keyId) {
        PublicKey cached = keys.get().get(keyId);
        if (cached != null) {
            return cached;
        }
        refreshIfAllowed();
        return keys.get().get(keyId);
    }

    private synchronized void refreshIfAllowed() {
        if (Duration.between(lastFetch.get(), Instant.now()).compareTo(MIN_REFETCH_INTERVAL) < 0) {
            return;
        }
        lastFetch.set(Instant.now());

        try {
            String body = restClient.get()
                    .uri(jwksUri)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                log.warn("JWKS 응답이 비어 있다 ({})", jwksUri);
                return;
            }

            JwkSet parsed = Jwks.setParser().build().parse(body);
            Map<String, PublicKey> loaded = new HashMap<>();
            for (Jwk<?> jwk : parsed.getKeys()) {
                if (jwk.toKey() instanceof PublicKey publicKey && jwk.getId() != null) {
                    loaded.put(jwk.getId(), publicKey);
                }
            }

            if (loaded.isEmpty()) {
                // 메인 백엔드가 아직 RS256 으로 전환하기 전이면 빈 목록이 정상이다.
                // 그동안은 HS256 경로로 검증되므로 오류가 아니다.
                log.debug("JWKS 에 공개키가 없다 — 아직 대칭키 발급 중인 것으로 본다");
            } else {
                log.info("JWKS 를 받았다 ({}개, kid={})", loaded.size(), loaded.keySet());
            }
            keys.set(Map.copyOf(loaded));
        } catch (Exception e) {
            // 실패해도 기존 캐시를 지우지 않는다 — 메인 백엔드가 잠깐 흔들린다고
            // 멀쩡히 검증되던 토큰이 전부 거부되면 안 된다.
            log.warn("JWKS 조회 실패 ({}): {}", jwksUri, e.getMessage());
        }
    }
}
