package com.voyageguard.sales.api;

import com.voyageguard.sales.api.dto.InventoryCreateRequest;
import com.voyageguard.sales.api.dto.InventoryRemainingResponse;
import com.voyageguard.sales.application.InventoryService;
import com.voyageguard.sales.application.inventory.InventoryConcurrencyStrategy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 재고 생성/잔여 조회 API. MSA 대비 1단계 - Planning이 회차 생성/조회 시 Sales의 도메인
 * 객체를 직접 안 부르고 이 API를 동기 REST로 호출하도록 하기 위해 신설.
 */
@Tag(name = "Inventory", description = "재고 생성/잔여 조회 API")
@RestController
@RequestMapping("/api/v1/inventories")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryConcurrencyStrategy inventoryConcurrencyStrategy;
    private final InventoryService inventoryService;

    @Operation(summary = "재고 생성", description = "회차 생성 시 정원만큼 재고를 초기화한다.")
    @PostMapping
    public Long create(@RequestBody InventoryCreateRequest request) {
        return inventoryService.create(request.departureId(), request.capacity());
    }

    @Operation(summary = "잔여 재고 조회", description = "회차의 현재 잔여 재고를 조회한다(활성화된 동시성 전략 기준).")
    @ApiResponse(responseCode = "404", description = "존재하지 않는 재고")
    @GetMapping("/{departureId}")
    public InventoryRemainingResponse getRemaining(@PathVariable Long departureId) {
        return new InventoryRemainingResponse(departureId, inventoryConcurrencyStrategy.getRemainingCount(departureId));
    }
}
