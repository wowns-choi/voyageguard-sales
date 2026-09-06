package com.voyageguard.sales.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record InventoryCreateRequest(
        @Schema(description = "회차 id") Long departureId,
        @Schema(description = "정원") Integer capacity) {
}
