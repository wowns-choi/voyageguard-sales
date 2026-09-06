package com.voyageguard.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 지금(진짜 Gateway가 없는 단계) Gateway 역할을 임시로 대신하는 필터 - 요청마다 JWT를 직접
 * 검증하고 principal에 memberId(Long)만 채운다. Auth 서비스의 JwtAuthenticationFilter와 동작은
 * 동일하되, 여기서는 검증 전용인 JwtVerifier(공개키만)를 쓴다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtVerifier jwtVerifier;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && jwtVerifier.validateToken(token)) {
            Long memberId = jwtVerifier.getMemberId(token);
            Authentication authentication = new UsernamePasswordAuthenticationToken(memberId, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
