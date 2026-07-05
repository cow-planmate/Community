package com.planmate.community.security;

import com.planmate.community.common.exception.CommunityException;
import com.planmate.community.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 검증 전용 JWT 프로바이더 — 이 서비스는 토큰을 발급하지 않는다.
 * 키 파생 방식은 jwt.secret-encoding으로 전환한다 (base64: 레거시 백엔드 페어링, raw: Backend-v2 페어링).
 */
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.secret-encoding:base64}")
    private String secretEncoding;

    private SecretKey key;

    @PostConstruct
    void init() {
        if ("base64".equals(secretEncoding)) {
            this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        } else {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
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
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new CommunityException(ErrorCode.UNAUTHORIZED);
        }
    }
}
