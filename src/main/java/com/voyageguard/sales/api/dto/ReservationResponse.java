package com.voyageguard.sales.api.dto;

import com.voyageguard.sales.domain.reservation.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record ReservationResponse(
        @Schema(description = "예약 id") Long id,
        @Schema(description = "회차 id") Long departureId,
        @Schema(description = "예약한 회원 id") Long memberId,
        @Schema(description = "예약 인원") Integer headcount,
        @Schema(description = "여행자명") String travelerName,
        @Schema(description = "예약 상태") ReservationStatus status,
        @Schema(description = "결제 유예시간 만료 시점") LocalDateTime expiresAt,
        @Schema(description = "예약금") Integer depositAmount,
        @Schema(description = "잔금") Integer balanceAmount) {
}
