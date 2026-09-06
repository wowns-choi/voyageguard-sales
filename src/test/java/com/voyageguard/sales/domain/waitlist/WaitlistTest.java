package com.voyageguard.sales.domain.waitlist;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaitlistTest {

    private Waitlist createWaitlist() {
        return createWaitlist(LocalDate.now().plusMonths(2));
    }

    private Waitlist createWaitlist(LocalDate saleEndDate) {
        return Waitlist.create(1L, 10L, 2, "홍길동", saleEndDate);
    }

    @Test
    void create_시_WAITING_상태로_생성된다() {
        Waitlist waitlist = createWaitlist();

        assertEquals(1L, waitlist.getDepartureId());
        assertEquals(10L, waitlist.getMemberId());
        assertEquals(2, waitlist.getHeadcount());
        assertEquals(WaitlistStatus.WAITING, waitlist.getStatus());
    }

    @Test
    void isOwnedBy_대기등록한_회원_id와_같으면_true를_반환한다() {
        Waitlist waitlist = createWaitlist();

        assertTrue(waitlist.isOwnedBy(10L));
        assertFalse(waitlist.isOwnedBy(99L));
    }

    @Test
    void WAITING_상태에서_promote_하면_PROMOTED로_전이된다() {
        Waitlist waitlist = createWaitlist();

        waitlist.promote();

        assertEquals(WaitlistStatus.PROMOTED, waitlist.getStatus());
    }

    @Test
    void WAITING이_아닌_상태에서_promote_하면_예외가_발생한다() {
        Waitlist waitlist = createWaitlist();
        waitlist.promote();

        assertThrows(IllegalStateException.class, waitlist::promote);
    }

    @Test
    void WAITING_상태에서_expire_하면_EXPIRED로_전이된다() {
        Waitlist waitlist = createWaitlist();

        waitlist.expire();

        assertEquals(WaitlistStatus.EXPIRED, waitlist.getStatus());
    }

    @Test
    void PROMOTED_상태에서_expire_하면_EXPIRED로_전이된다() {
        Waitlist waitlist = createWaitlist();
        waitlist.promote();

        waitlist.expire();

        assertEquals(WaitlistStatus.EXPIRED, waitlist.getStatus());
    }

    @Test
    void WAITING도_PROMOTED도_아닌_상태에서_expire_하면_예외가_발생한다() {
        Waitlist waitlist = createWaitlist();
        waitlist.expire();

        assertThrows(IllegalStateException.class, waitlist::expire);
    }

    @Test
    void create_시_expiresAt은_등록_시점_3일_후다() {
        Waitlist waitlist = createWaitlist();

        assertEquals(waitlist.getCreatedAt().plusDays(3), waitlist.getExpiresAt());
    }

    @Test
    void create_시_판매종료일이_3일보다_가까우면_expiresAt은_판매종료일이다() {
        LocalDate saleEndDate = LocalDate.now();
        Waitlist waitlist = createWaitlist(saleEndDate);

        assertEquals(saleEndDate.atStartOfDay(), waitlist.getExpiresAt());
    }

    @Test
    void promote_시_expiresAt이_승격_시점_24시간_후로_재계산된다() {
        Waitlist waitlist = createWaitlist();

        waitlist.promote();

        assertEquals(waitlist.getPromotedAt().plusHours(24), waitlist.getExpiresAt());
    }

    @Test
    void promote_시_판매종료일이_24시간보다_가까우면_expiresAt은_판매종료일이다() {
        LocalDate saleEndDate = LocalDate.now();
        Waitlist waitlist = createWaitlist(saleEndDate);

        waitlist.promote();

        assertEquals(saleEndDate.atStartOfDay(), waitlist.getExpiresAt());
    }
}
