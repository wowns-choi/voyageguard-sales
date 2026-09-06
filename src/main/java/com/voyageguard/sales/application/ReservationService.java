package com.voyageguard.sales.application;

import com.voyageguard.common.exception.AuthenticationFailedException;
import com.voyageguard.common.exception.AuthorizationFailedException;
import com.voyageguard.common.security.CurrentMember;
import com.voyageguard.sales.infrastructure.outbox.SalesOutboxEvent;
import com.voyageguard.sales.infrastructure.outbox.SalesOutboxEventRepository;
import com.voyageguard.sales.api.dto.ReservationResponse;
import com.voyageguard.sales.application.departure.DepartureClient;
import com.voyageguard.sales.application.departure.DepartureView;
import com.voyageguard.sales.application.inventory.InventoryConcurrencyStrategy;
import com.voyageguard.sales.domain.reservation.Reservation;
import com.voyageguard.sales.domain.reservation.ReservationCancelledEvent;
import com.voyageguard.sales.domain.reservation.ReservationConfirmFailedEvent;
import com.voyageguard.sales.domain.reservation.ReservationRepository;
import com.voyageguard.sales.domain.reservation.ReservationStatus;
import com.voyageguard.sales.infrastructure.redis.WaitlistRankRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final DepartureClient departureClient;
    private final InventoryConcurrencyStrategy inventoryConcurrencyStrategy;
    private final SalesOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final WaitlistRankRepository waitlistRankRepository;
    private final CurrentMember currentMember;

    // MSA 대비 1단계: Planning의 Departure를 DB로 직접 안 읽고 DepartureClient(동기 REST)로 조회
    public Long request(Long departureId, Integer headcount, String travelerName) {
        Long memberId = requireLogin();

        DepartureView departure = departureClient.get(departureId);
        if (departure.status() != DepartureView.Status.OPEN) {
            throw new IllegalStateException("모집중 상태의 회차만 예약할 수 있습니다. 현재 상태: " + departure.status());
        }
        // 대기열이 있으면 새치기 방지 - 신규 예약을 막고 대기 등록으로 유도
        if (waitlistRankRepository.hasWaiting(departureId)) {
            throw new IllegalStateException("대기 중인 인원이 있어 새 예약을 받을 수 없습니다. 대기 등록을 이용해주세요.");
        }

        inventoryConcurrencyStrategy.decrease(departureId, headcount);

        Reservation reservation = Reservation.create(departureId, memberId, headcount, travelerName, departure.saleEndDate(), departure.salePrice());
        return reservationRepository.save(reservation).getId();
    }

    // 본인 예약만 취소 가능 - 예약 ID만 알면 아무나 취소할 수 있던 문제를 막기 위함
    public void cancel(Long id) {
        Reservation reservation = getReservation(id);
        requireOwnership(reservation);
        reservation.cancel();
        releaseInventoryAndNotify(reservation, "ReservationCancelled");
    }

    /**
     * Payment의 PaymentApproved 이벤트를 받아 예약을 확정한다.
     * 1) REQUESTED -> 그대로 확정.
     * 2) CONFIRMED -> 이미 확정됨(다른 결제 건이 먼저 확정시켰거나 이벤트 중복 수신) - 멱등 처리.
     * 3) EXPIRED ->
     *   결제 유예시간(10분)이 지나서 예약을 만료 처리할 때 이미 재고를 반납했으므로, 확정 전에 재고를 다시 확보해야 함.
     *   그 사이 다른 손님이 채갔으면 재고가 부족할 수 있음. 그럴 땐 확정 대신 보상(환불) 요청.
     * 4) CANCELLED -> 고객이 직접 취소한 것을 뒤늦은 결제로 되살리면 안 되므로 보상(환불) 요청.
     */
    public void confirmFromPayment(Long paymentId, Long reservationId) {
        Reservation reservation = getReservation(reservationId);

        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            return;
        }

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            notifyConfirmFailed(paymentId, reservationId, "고객이 이미 취소한 예약입니다."); // 환불 요청
            return;
        }

        if (reservation.getStatus() == ReservationStatus.EXPIRED) {
            if (inventoryConcurrencyStrategy.getRemainingCount(reservation.getDepartureId()) < reservation.getHeadcount()) {
                notifyConfirmFailed(paymentId, reservationId, "만료 후 재고가 소진되어 확정할 수 없습니다."); // 환불 요청
                return;
            }
            inventoryConcurrencyStrategy.decrease(reservation.getDepartureId(), reservation.getHeadcount());
        }

        reservation.confirm();
    }

    // MSA 대비 1단계: Payment가 예약 상태를 직접 DB로 안 읽고 이 API(동기 REST)로 조회하게 함.
    // 소유권 검증을 여기서 안 하는 이유: Payment가 로그인 세션 없이(서버 간 호출로) 이 API를
    // 그대로 호출하는 기존 계약이 있어, 여기서 막으면 그 호출까지 깨짐 - 소유권 검증은 응답에
    // 담긴 memberId를 갖고 각 호출자(cancel(), PaymentService.request())가 직접 하도록 함.
    // "조회 자체"의 정보 노출(로그인만 하면 남의 예약 ID로 조회 가능)은 아직 남은 문제로 별도 처리 필요.
    @Transactional(readOnly = true)
    public ReservationResponse get(Long id) {
        Reservation reservation = getReservation(id);
        return new ReservationResponse(
                reservation.getId(),
                reservation.getDepartureId(),
                reservation.getMemberId(),
                reservation.getHeadcount(),
                reservation.getTravelerName(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getDepositAmount(),
                reservation.getBalanceAmount()
        );
    }

    /**
     * "예약 -> 재고 있나? -> 있다 -> 결제" 로 간 경우,
     * 결제 유예시간(Reservation.expiresAt) 안에 결제하지 않은 예약은 만료시킨다.
     */
    @Scheduled(fixedDelay = 60000) // 1분마다 - 유예기간 자체가 10분으로 짧아서 스캔 주기도 짧게
    public void expireStaleReservations() {
        List<Reservation> targets = reservationRepository.findByStatusAndExpiresAtBefore(
                ReservationStatus.REQUESTED, LocalDateTime.now());

        for (Reservation reservation : targets) {
            reservation.expire(); // 대기열 만료(X), 예약 만료(O)
            releaseInventoryAndNotify(reservation, "ReservationExpired");
        }
    }

    /**
     * 재고 반납 + 대기열 재평가 트리거(Outbox 경유).
     *
     * 취소든 만료든 "이 회차 재고가 늘었다"는 사실은 같아서 로직은 공유하고,
     * eventType 라벨만 다르게 남겨 원인을 구분해둔다.
     */
    private void releaseInventoryAndNotify(Reservation reservation, String eventType) {

        // 재고 반납
        inventoryConcurrencyStrategy.increase(reservation.getDepartureId(), reservation.getHeadcount());

        // Kafka로 바로 안 보내고, 같은 트랜잭션 안에서 outbox 테이블에 "보낼 것"만 원자적으로 기록
        ReservationCancelledEvent event = new ReservationCancelledEvent(reservation.getDepartureId(), reservation.getHeadcount());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new IllegalStateException(eventType + " 직렬화 실패", e);
        }
        outboxEventRepository.save(
                SalesOutboxEvent.create(
                        eventType,
                        "reservation.cancelled", // 토픽 : "예약이 취소됨" - 원인(취소/만료)과 무관하게 구독측 처리는 동일
                        reservation.getDepartureId().toString(), // Key : 회차 id
                        payload // 회차 id, 인원수
                )
        );
    }

    // 예약 확정 실패를 Payment에 알려 보상(환불)을 요청하는 이벤트 발행
    private void notifyConfirmFailed(Long paymentId, Long reservationId, String reason) {
        ReservationConfirmFailedEvent event = new ReservationConfirmFailedEvent(paymentId, reservationId, reason);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new IllegalStateException("ReservationConfirmFailed 직렬화 실패", e);
        }
        outboxEventRepository.save(
                SalesOutboxEvent.create(
                        "ReservationConfirmFailed",
                        "reservation.confirm-failed",
                        paymentId.toString(),
                        payload
                )
        );
    }

    private Long requireLogin() {
        return currentMember.memberId()
                .orElseThrow(() -> new AuthenticationFailedException("로그인이 필요합니다."));
    }

    private void requireOwnership(Reservation reservation) {
        if (!reservation.isOwnedBy(requireLogin())) {
            throw new AuthorizationFailedException("본인의 예약만 취소할 수 있습니다.");
        }
    }

    private Reservation getReservation(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다. id=" + id));
    }
}
