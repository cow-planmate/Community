package com.planmate.community.security;

import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.UUID;

/**
 * 검증 전용 JWT 프로바이더 — 이 서비스는 토큰을 발급하지 않는다.
 *
 * <p>RS256 토큰은 메인 백엔드의 JWKS 공개키로 검증한다. 이게 목표 상태다 —
 * 검증만 하는 쪽이 서명 능력까지 갖지 않는다. 예전에는 같은 HS256 대칭키를 나눠 가져서
 * 이 서비스가 침해당하면 메인 백엔드의 토큰을 위조할 수 있었다.
 *
 * <p>HS256 경로는 <b>전환 기간에만</b> 남긴다. 메인 백엔드가 RS256 으로 넘어가는 시점에
 * 이미 발급된 access 토큰(15분)이 살아 있어서, 이 경로를 먼저 지우면 그만큼 사용자가
 * 로그아웃된다. 배포 후 15분이 지나면 jwt.secret 주입과 함께 제거할 것.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** 전환 기간 전용 — RS256 전환이 끝나면 이 필드와 함께 제거한다. */
    @Value("${jwt.secret:}")
    private String secret;

    @Value("${jwt.secret-encoding:base64}")
    private String secretEncoding;

    private final JwksKeyProvider jwksKeyProvider;

    private SecretKey legacyKey;

    public JwtTokenProvider(JwksKeyProvider jwksKeyProvider) {
        this.jwksKeyProvider = jwksKeyProvider;
    }

    @PostConstruct
    void init() {
        if (secret == null || secret.isBlank()) {
            log.info("jwt.secret 이 없다 — RS256(JWKS)으로만 검증한다. 전환 완료 상태.");
            return;
        }
        this.legacyKey = "base64".equals(secretEncoding)
                ? Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))
                : Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 헤더의 alg 를 보고 검증 키를 고른다.
     *
     * <p>alg 별로 <b>정해진 타입의 키만</b> 돌려주므로, RS256 토큰을 HS256 으로 바꿔 공개키를
     * HMAC 비밀키 자리에 밀어 넣는 고전적인 혼동 공격이 성립하지 않는다. 목록에 없는 alg 는
     * 전부 거부한다 — alg=none 도 여기서 막힌다.
     */
    private Key resolveKey(Header header) {
        String alg = header.getAlgorithm();
        if (alg == null) {
            throw new CommunityException(ErrorCode.UNAUTHORIZED);
        }

        if (alg.startsWith("RS")) {
            String keyId = header instanceof ProtectedHeader protectedHeader
                    ? protectedHeader.getKeyId()
                    : null;
            if (keyId == null) {
                throw new CommunityException(ErrorCode.UNAUTHORIZED);
            }
            Key key = jwksKeyProvider.find(keyId);
            if (key == null) {
                // 공개키를 못 구했다 = 검증할 수 없다. 통과시키지 않는다.
                log.warn("JWKS 에서 kid={} 를 찾지 못해 토큰을 거부한다", keyId);
                throw new CommunityException(ErrorCode.UNAUTHORIZED);
            }
            return key;
        }

        // HS256 만 받으면 안 된다 — 발급측 jjwt 가 키 길이에 맞는 알고리즘을 자동으로 고르며,
        // 현재 공유 secret 으로는 실제로 HS384 가 나온다. HS256 만 통과시키면 전환 기간에
        // 멀쩡한 기존 토큰이 전부 거부되어 사용자가 로그아웃된다.
        if (alg.startsWith("HS") && legacyKey != null) {
            return legacyKey;
        }

        throw new CommunityException(ErrorCode.UNAUTHORIZED);
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public boolean isAccessToken(String token) {
        return "access".equals(parseClaims(token).get("typ", String.class));
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().keyLocator(this::resolveKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException | CommunityException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .keyLocator(this::resolveKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new CommunityException(ErrorCode.UNAUTHORIZED);
        }
    }
}
