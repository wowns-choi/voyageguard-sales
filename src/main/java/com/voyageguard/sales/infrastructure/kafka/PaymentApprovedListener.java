package com.voyageguard.sales.infrastructure.kafka;

import com.voyageguard.sales.application.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Payment가 발행하는 PaymentApproved 이벤트를 구독해서 예약을 확정한다.
 */
@Component
@RequiredArgsConstructor
public class PaymentApprovedListener {

    private final ReservationService reservationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment.approved", groupId = "reservation-confirm")
    public void handle(String payload) {
        PaymentApprovedPayload event = objectMapper.readValue(payload, PaymentApprovedPayload.class);
        reservationService.confirmFromPayment(event.paymentId(), event.reservationId());
    }

    private record PaymentApprovedPayload(Long paymentId, Long reservationId) {
    }
}
