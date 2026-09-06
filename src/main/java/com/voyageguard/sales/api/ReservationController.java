package com.voyageguard.sales.api;

import com.voyageguard.sales.api.dto.ReservationCreateRequest;
import com.voyageguard.sales.api.dto.ReservationResponse;
import com.voyageguard.sales.application.ReservationService;
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

@Tag(name = "Reservation", description = "예약 요청/취소 API")
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "예약 요청", description = "모집중 상태의 회차에 대해 예약을 요청하고 재고를 차감한다.")
    @ApiResponse(responseCode = "401", description = "로그인 필요")
    @ApiResponse(responseCode = "404", description = "존재하지 않는 회차 또는 재고")
    @ApiResponse(responseCode = "409", description = "모집중 상태가 아니거나 잔여 재고가 부족해 예약 불가")
    @PostMapping
    public Long request(@RequestBody ReservationCreateRequest request) {
        return reservationService.request(request.departureId(), request.headcount(), request.travelerName());
    }

    @Operation(summary = "예약 상세", description = "예약 하나를 조회한다.")
    @ApiResponse(responseCode = "404", description = "존재하지 않는 예약")
    @GetMapping("/{id}")
    public ReservationResponse get(@PathVariable Long id) {
        return reservationService.get(id);
    }

    @Operation(summary = "예약 취소", description = "예약요청 또는 확정 상태의 예약을 취소하고 재고를 복구한다.")
    @ApiResponse(responseCode = "401", description = "로그인 필요")
    @ApiResponse(responseCode = "403", description = "본인의 예약이 아님")
    @ApiResponse(responseCode = "404", description = "존재하지 않는 예약 또는 재고")
    @ApiResponse(responseCode = "409", description = "예약요청 또는 확정 상태가 아니어서 취소 불가")
    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable Long id) {
        reservationService.cancel(id);
    }
}
