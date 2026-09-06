package com.voyageguard.sales.domain.reservation;

public record ReservationCancelledEvent(Long departureId, Integer headcount) {
}
