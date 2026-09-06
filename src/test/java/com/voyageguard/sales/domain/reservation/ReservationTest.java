package com.voyageguard.sales.domain.reservation;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservationTest {

    private Reservation createReservation() {
        return createReservation(LocalDate.now().plusMonths(2));
    }

    private Reservation createReservation(LocalDate saleEndDate) {
        return Reservation.create(1L, 10L, 2, "홍길동", saleEndDate, 100_000);
    }

    @Test
    void create_시_REQUESTED_상태로_생성된다() {
        Reservation reservation = createReservation();

        assertEquals(1L, reservation.getDepartureId());
        assertEquals(10L, reservation.getMemberId());
        assertEquals(2, reservation.getHeadcount());
        assertEquals(ReservationStatus.REQUESTED, reservation.getStatus());
    }

    @Test
    void isOwnedBy_예약한_회원_id와_같으면_true를_반환한다() {
        Reservation reservation = createReservation();

        assertTrue(reservation.isOwnedBy(10L));
        assertFalse(reservation.isOwnedBy(99L));
    }

    @Test
    void create_시_예약금은_총액의_10퍼센트다() {
        Reservation reservation = createReservation();

        assertEquals(20_000, reservation.getDepositAmount()); // 100_000 * 2 * 0.1
    }

    @Test
    void create_시_잔금은_총액에서_예약금을_뺀_금액이다() {
        Reservation reservation = Reservation.create(1L, 10L, 3, "홍길동", LocalDate.now().plusMonths(2), 100_001);

        // totalAmount(300_003) * 0.1 = 30_000.3 -> depositAmount는 30_000으로 버려짐
        assertEquals(30_000, reservation.getDepositAmount());
        // balanceAmount를 비율로 다시 계산하지 않고 나머지로 구해서, 버림으로 생기는 오차 없이 합이 totalAmount와 정확히 일치한다
        assertEquals(270_003, reservation.getBalanceAmount());
        assertEquals(300_003, reservation.getDepositAmount() + reservation.getBalanceAmount());
    }

    @Test
    void REQUESTED_상태에서_confirm_하면_CONFIRMED로_전이된다() {
        Reservation reservation = createReservation();

        reservation.confirm();

        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
    }

    @Test
    void REQUESTED가_아닌_상태에서_confirm_하면_예외가_발생한다() {
        Reservation reservation = createReservation();
        reservation.confirm();

        assertThrows(IllegalStateException.class, reservation::confirm);
    }

    @Test
    void EXPIRED_상태에서_confirm_하면_CONFIRMED로_전이된다() {
        Reservation reservation = createReservation();
        reservation.expire();

        reservation.confirm();

        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
    }

    @Test
    void CANCELLED_상태에서_confirm_하면_예외가_발생한다() {
        Reservation reservation = createReservation();
        reservation.cancel();

        assertThrows(IllegalStateException.class, reservation::confirm);
    }

    @Test
    void REQUESTED_상태에서_cancel_하면_CANCELLED로_전이된다() {
        Reservation reservation = createReservation();

        reservation.cancel();

        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
    }

    @Test
    void CONFIRMED_상태에서_cancel_하면_CANCELLED로_전이된다() {
        Reservation reservation = createReservation();
        reservation.confirm();

        reservation.cancel();

        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
    }

    @Test
    void REQUESTED나_CONFIRMED가_아닌_상태에서_cancel_하면_예외가_발생한다() {
        Reservation reservation = createReservation();
        reservation.cancel();

        assertThrows(IllegalStateException.class, reservation::cancel);
    }

    @Test
    void create_시_expiresAt은_요청_시점_10분_후다() {
        Reservation reservation = createReservation();

        assertEquals(reservation.getRequestedAt().plusMinutes(10), reservation.getExpiresAt());
    }

    @Test
    void create_시_판매종료일이_10분보다_가까우면_expiresAt은_판매종료일이다() {
        LocalDate saleEndDate = LocalDate.now();
        Reservation reservation = createReservation(saleEndDate);

        assertEquals(saleEndDate.atStartOfDay(), reservation.getExpiresAt());
    }

    @Test
    void REQUESTED_상태에서_expire_하면_EXPIRED로_전이된다() {
        Reservation reservation = createReservation();

        reservation.expire();

        assertEquals(ReservationStatus.EXPIRED, reservation.getStatus());
    }

    @Test
    void REQUESTED가_아닌_상태에서_expire_하면_예외가_발생한다() {
        Reservation reservation = createReservation();
        reservation.confirm();

        assertThrows(IllegalStateException.class, reservation::expire);
    }
}
