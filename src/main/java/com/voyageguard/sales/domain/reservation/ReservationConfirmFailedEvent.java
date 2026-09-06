package com.voyageguard.sales.domain.reservation;

public record ReservationConfirmFailedEvent(Long paymentId, Long reservationId, String reason) {
}
