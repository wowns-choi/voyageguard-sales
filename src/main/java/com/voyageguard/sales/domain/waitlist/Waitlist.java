package com.voyageguard.sales.domain.waitlist;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재고 부족 시 대기 등록한 건의 상태 전이(WAITING -> PROMOTED/EXPIRED)를 캡슐화한 Aggregate Root.
 * 실제 대기 순번은 이 엔티티가 아니라 Redis Sorted Set이 원천(source of truth) - departureId별로
 * 여러 대기자의 순번을 원자적으로 관리해야 해서 RDB 비관적 락보다 Redis ZADD/ZRANK가 적합하기
 * 때문이다 (CLAUDE.md "Redis - 용도별 자료구조 구분" 참고). 이 엔티티는 대기 건의 상태(승격/만료
 * 여부)만 갖는다.
 * promote()/expire()는 원래 ReservationCancelled 이벤트(Kafka)가 트리거지만, 아직 Kafka
 * 연동이 없어 지금은 Service/Controller에서 호출하지 않는다 - Kafka 이벤트 흐름 붙을 때 이어서
 * 연결.
 */
@Entity
@Table(indexes = @Index(name = "idx_waitlist_status_expires_at", columnList = "status, expiresAt"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Waitlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Departure 를 객체가 아닌 ID로 참조한다 (Reference Other Aggregates by Identity Only)
    private Long departureId;

    // 대기 등록한 회원(Auth BC의 Member)을 ID로만 참조 - 소유권 검증(내 순번 조회 시 본인 것인지)에 사용
    private Long memberId;

    private Integer headcount;

    private String travelerName;

    @Enumerated(EnumType.STRING)
    private WaitlistStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime promotedAt;

    // 쿼리 성능 최적화를 위해, Waitlist 테이블에 회차의 판매종료일을 담아둡니다.
    private LocalDate saleEndDate;

    // 만료 스케줄러가 상태별 계산 없이 "expiresAt <= now"만 인덱스로 조회할 수 있도록,
    // 만료 시점을 상태가 바뀔 때마다(생성/승격) 미리 계산해서 저장해둔다.
    private LocalDateTime expiresAt;

    // WAITING은 재고를 차지한 게 없어 여유롭게(3일),
    // PROMOTED는 재고를 실제로 선점하고 있어 짧게(24시간)
    private static final long WAITING_MAX_WAIT_DAYS = 3;
    private static final long PROMOTED_GRACE_HOURS = 24;

    private Waitlist(Long departureId, Long memberId, Integer headcount, String travelerName, LocalDate saleEndDate) {
        this.departureId = departureId;
        this.memberId = memberId;
        this.headcount = headcount;
        this.travelerName = travelerName;
        this.status = WaitlistStatus.WAITING;
        this.createdAt = LocalDateTime.now();
        this.saleEndDate = saleEndDate;
        this.expiresAt = capBySaleEnd(createdAt.plusDays(WAITING_MAX_WAIT_DAYS));
    }

    public static Waitlist create(Long departureId, Long memberId, Integer headcount, String travelerName, LocalDate saleEndDate) {
        return new Waitlist(departureId, memberId, headcount, travelerName, saleEndDate);
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public void promote() {
        if (status != WaitlistStatus.WAITING) {
            throw new IllegalStateException("대기중 상태에서만 승격할 수 있습니다. 현재 상태: " + status);
        }
        this.status = WaitlistStatus.PROMOTED;
        this.promotedAt = LocalDateTime.now();
        this.expiresAt = capBySaleEnd(promotedAt.plusHours(PROMOTED_GRACE_HOURS));
    }

    /**
     * 1. PROMOTED 뿐만 아니라, WAITING 도 만료대상이 될 수 있다
     *      - 예를 들어, 원하는 예약인원이 20명인데, 지금 꽉차서 최대 15명까지만 자리가 날 가능성이 있는 경우,
     *        이 WAITING 상태인 대기자를 만료시키지 않으면 15자리를 낭비하게 됨.
     * 2. PROMOTED 는 승격 후, 결제 안한 경우 만료시켜야 하므로 당연히 만료대상이 될 수 있다.
     * */
    public void expire() {
        if (status != WaitlistStatus.WAITING && status != WaitlistStatus.PROMOTED) {
            throw new IllegalStateException("대기중 또는 승격 상태에서만 만료시킬 수 있습니다. 현재 상태: " + status);
        }
        this.status = WaitlistStatus.EXPIRED;
    }

    // 하드캡과 판매종료일 중 더 이른 시점을 만료 시점으로 삼는다.
    private LocalDateTime capBySaleEnd(LocalDateTime hardCap) {
        LocalDateTime saleEnd = saleEndDate.atStartOfDay();
        return hardCap.isBefore(saleEnd) ? hardCap : saleEnd;
    }
}
