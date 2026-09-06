package com.voyageguard.sales.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record InventoryRemainingResponse(
        @Schema(description = "회차 id") Long departureId,
        @Schema(description = "잔여 좌석") Integer remainingCount) {
}
