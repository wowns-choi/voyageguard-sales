package com.voyageguard.common.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * "지금 이 요청을 보낸 회원이 누구냐"를 다른 BC가 물어볼 수 있게 하는 창구.
 * JWT/OAuth를 전혀 몰라도 됨 - SecurityContext에 principal로 들어있는 memberId만 꺼내줌.
 * 나중에 진짜 Gateway로 분리되면, 이 클래스 내부만 "헤더에서 읽기"로 바뀌고 호출부(Sales/Payment
 * 등)는 안 바뀐다.
 */
@Component
public class CurrentMember {

    public Optional<Long> memberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long memberId)) {
            return Optional.empty();
        }
        return Optional.of(memberId);
    }
}
