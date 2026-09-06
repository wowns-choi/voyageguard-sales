package com.voyageguard.sales.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record WaitlistCreateRequest(
        @Schema(description = "회차 id") Long departureId,
        @Schema(description = "대기 인원") Integer headcount,
        @Schema(description = "여행자명") String travelerName) {
}
