package com.voyageguard.common.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Auth 서비스가 발급한 JWT를 검증만 한다(공개키만 가짐, 발급 능력 없음).
 * 진짜 Gateway가 아직 없어서, 소유권 검증(CurrentMember)이 필요한 서비스(Sales/Payment)가
 * 각자 이 역할을 임시로 대신한다 - 개인키는 Auth 서비스에만 있어야 하므로 여기엔 절대 두지 않음.
 * 나중에 진짜 Gateway로 분리되면 이 클래스는 통째로 사라지고 헤더만 읽는 걸로 대체된다.
 */
@Component
public class JwtVerifier {

    @Value("${jwt.public-key}")
    private String publicKeyValue;

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(publicKey()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getMemberId(String token) {
        String subject = Jwts.parser()
                .verifyWith(publicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return Long.parseLong(subject);
    }

    private PublicKey publicKey() {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(publicKeyValue);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 공개키 로딩 실패", e);
        }
    }
}
