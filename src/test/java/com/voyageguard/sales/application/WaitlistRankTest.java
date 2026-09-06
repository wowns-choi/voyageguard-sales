package com.voyageguard.sales.application;

import com.voyageguard.sales.application.departure.DepartureClient;
import com.voyageguard.sales.application.departure.DepartureView;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 실제 Redis(Sorted Set)를 사용해 대기 등록 순서대로 순번이 매겨지는지 검증한다.
 * 로컬 인프라(docker compose up -d)가 필요해 @Tag("integration")으로 분리.
 * Planning이 별도 서비스로 분리된 뒤라 Departure를 DB로 직접 만들 수 없어, DepartureClient를
 * 테스트 전용 스텁으로 대체한다(항상 OPEN 상태의 회차를 반환).
 */
@Tag("integration")
@SpringBootTest
class WaitlistRankTest {

    @Autowired
    private WaitlistService waitlistService;

    @BeforeEach
    void login() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of()));
    }

    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 여러_명이_순서대로_대기_등록하면_등록한_순서대로_순번이_매겨진다() {
        // 매 실행마다 유니크한 departureId를 써서, Redis에 남아있는 이전 실행의 순번 데이터와
        // 섞이지 않게 한다(원래는 Departure 자동증가 id를 그대로 썼지만, 이제 Departure를
        // 직접 저장하지 않으므로 시각 기반으로 대체).
        Long departureId = System.currentTimeMillis();

        Long first = waitlistService.join(departureId, 2, "첫번째");
        Long second = waitlistService.join(departureId, 1, "두번째");
        Long third = waitlistService.join(departureId, 3, "세번째");

        assertEquals(1L, waitlistService.rank(first));
        assertEquals(2L, waitlistService.rank(second));
        assertEquals(3L, waitlistService.rank(third));
    }

    @TestConfiguration
    static class StubDepartureConfig {
        @Bean
        @Primary
        DepartureClient departureClient() {
            return departureId -> new DepartureView(
                    DepartureView.Status.OPEN, 30, LocalDate.of(2026, 12, 10), 1500000);
        }
    }
}
