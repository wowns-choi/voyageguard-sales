package com.voyageguard.sales.api;

import com.voyageguard.sales.api.dto.WaitlistCreateRequest;
import com.voyageguard.sales.application.WaitlistService;
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

@Tag(name = "Waitlist", description = "재고 부족 시 대기 등록/순번 조회 API")
@RestController
@RequestMapping("/api/v1/waitlists")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @Operation(summary = "대기 등록", description = "모집중 상태의 회차에 재고 부족으로 대기를 등록한다.")
    @ApiResponse(responseCode = "401", description = "로그인 필요")
    @ApiResponse(responseCode = "404", description = "존재하지 않는 회차")
    @ApiResponse(responseCode = "409", description = "모집중 상태가 아니어서 대기 등록 불가")
    @PostMapping
    public Long join(@RequestBody WaitlistCreateRequest request) {
        return waitlistService.join(request.departureId(), request.headcount(), request.travelerName());
    }

    @Operation(summary = "내 순번 조회", description = "Redis Sorted Set 기준 현재 대기 순번(1부터 시작)을 조회한다.")
    @ApiResponse(responseCode = "401", description = "로그인 필요")
    @ApiResponse(responseCode = "403", description = "본인의 대기열이 아님")
    @ApiResponse(responseCode = "404", description = "존재하지 않는 대기열")
    @GetMapping("/{id}/rank")
    public Long rank(@PathVariable Long id) {
        return waitlistService.rank(id);
    }
}
