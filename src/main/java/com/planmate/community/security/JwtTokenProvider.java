package com.planmate.community.security;

import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.ProtectedHeader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.UUID;

/**
 * 검증 전용 JWT 프로바이더 — 이 서비스는 토큰을 발급하지 않는다.
 *
 * <p>RS256 토큰은 메인 백엔드의 JWKS 공개키로 검증한다. 이게 목표 상태다 —
 * 검증만 하는 쪽이 서명 능력까지 갖지 않는다. 예전에는 같은 HS256 대칭키를 나눠 가져서
 * 이 서비스가 침해당하면 메인 백엔드의 토큰을 위조할 수 있었다.
 *
 * <p>HS256 경로는 2026-08-22 에 걷어냈다. 전환기(2026-08-02)에 이미 발급된 access 토큰이
 * 소진될 때까지만 필요했던 유예인데, 이 서비스에는 그 뒤로 {@code jwt.secret} 이 주입되지
 * 않았으므로 실제로는 오래 전부터 죽은 코드였다. 되살리지 말 것 — 대칭키를 다시 들이면
 * 이 서비스가 메인 백엔드의 토큰을 위조할 수 있게 된다.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final JwksKeyProvider jwksKeyProvider;

    public JwtTokenProvider(JwksKeyProvider jwksKeyProvider) {
        this.jwksKeyProvider = jwksKeyProvider;
    }

    /**
     * 헤더의 alg 를 보고 검증 키를 고른다.
     *
     * <p>RS 일 때만 JWKS 공개키를 돌려주고 나머지는 전부 거부한다 — HS 로 바꿔 공개키를 HMAC
     * 비밀키 자리에 밀어 넣는 고전적인 혼동 공격도, alg=none 도 여기서 막힌다.
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

        // RS 가 아니면 전부 거부한다. HS 분기는 전환기 유예였고 이제 없다.
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
