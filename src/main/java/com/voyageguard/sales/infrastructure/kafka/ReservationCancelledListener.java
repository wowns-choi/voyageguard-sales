package com.voyageguard.sales.infrastructure.kafka;

import com.voyageguard.sales.application.WaitlistService;
import com.voyageguard.sales.domain.reservation.ReservationCancelledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * ReservationCancelledEvent(Outbox 릴레이가 발행)를 구독해서 대기열 승격을 트리거한다.
 * Kafka 레벨에서는 payload가 그냥 JSON 문자열이라(Outbox 설계 참고), 여기서 직접
 * ObjectMapper로 역직렬화한다.
 */
@Component
@RequiredArgsConstructor
public class ReservationCancelledListener {

    private final WaitlistService waitlistService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "reservation.cancelled", groupId = "waitlist-promotion")
    public void handle(String payload) {
        ReservationCancelledEvent event = objectMapper.readValue(payload, ReservationCancelledEvent.class);
        waitlistService.promoteNext(event.departureId());
    }
}
