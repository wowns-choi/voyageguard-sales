package com.voyageguard.sales.domain.reservation;

public enum ReservationStatus {
    REQUESTED, // 예약요청됨(재고 차감 완료, 결제 대기)
    CONFIRMED, // 확정됨(결제완료)
    CANCELLED, // 취소됨(고객이 직접 취소)
    EXPIRED // 만료됨 : 결제 유예시간 안에 결제 안 함
}
