package com.voyageguard.sales.application;

import com.voyageguard.common.exception.AuthenticationFailedException;
import com.voyageguard.common.exception.AuthorizationFailedException;
import com.voyageguard.common.security.CurrentMember;
import com.voyageguard.sales.application.departure.DepartureClient;
import com.voyageguard.sales.application.departure.DepartureView;
import com.voyageguard.sales.application.inventory.InventoryConcurrencyStrategy;
import com.voyageguard.sales.domain.waitlist.Waitlist;
import com.voyageguard.sales.domain.waitlist.WaitlistRepository;
import com.voyageguard.sales.domain.waitlist.WaitlistStatus;
import com.voyageguard.sales.infrastructure.redis.WaitlistRankRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class WaitlistService {
    private final WaitlistRepository waitlistRepository;
    private final DepartureClient departureClient;
    private final WaitlistRankRepository waitlistRankRepository;
    private final InventoryConcurrencyStrategy inventoryConcurrencyStrategy;
    private final CurrentMember currentMember;

    // MSA 대비 1단계: Planning의 Departure를 DB로 직접 안 읽고 DepartureClient(동기 REST)로 조회
    public Long join(Long departureId, Integer headcount, String travelerName) {
        Long memberId = requireLogin();

        DepartureView departure = departureClient.get(departureId);
        if (departure.status() != DepartureView.Status.OPEN) {
            throw new IllegalStateException("모집중 상태의 회차만 대기 등록할 수 있습니다. 현재 상태: " + departure.status());
        }

        // 정원보다 많은 인원으로 등록하면 절대 승격될 수 없어(전원 취소해도 못 채움),
        // 뒷사람을 영원히 막는 대기자가 생김
        if (headcount > departure.capacity()) {
            throw new IllegalStateException(
                    "정원(" + departure.capacity() + "명)보다 많은 인원으로는 대기 등록할 수 없습니다. 요청 인원: " + headcount);
        }

        Waitlist waitlist = Waitlist.create(departureId, memberId, headcount, travelerName, departure.saleEndDate());
        Long id = waitlistRepository.save(waitlist).getId();
        try {
            waitlistRankRepository.add(departureId, id);
        } catch (DataAccessException e) {
            waitlistRepository.deleteById(id);
            throw e;
        }

        return id;
    }

    // 본인 대기열만 순번 조회 가능 - Waitlist는 Reservation.get()과 달리 다른 BC가 의존하는
    // 내부 API가 아니라 순수 고객용 API라서, 소유권 검증을 여기서 바로 걸어도 기존 계약이 안 깨짐
    @Transactional(readOnly = true)
    public Long rank(Long id) {
        Waitlist waitlist = waitlistRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 대기열입니다. id=" + id));
        if (!waitlist.isOwnedBy(requireLogin())) {
            throw new AuthorizationFailedException("본인의 대기열만 조회할 수 있습니다.");
        }
        return waitlistRankRepository.rank(waitlist.getDepartureId(), id);
    }

    private Long requireLogin() {
        return currentMember.memberId()
                .orElseThrow(() -> new AuthenticationFailedException("로그인이 필요합니다."));
    }

    /**
     * 예약 취소 -> 재고+1 -> 대기열 승격(WAITING -> PROMOTED) -> 재고-1
     * 과정에서, 대기열 승격과 재고-1 부분을 처리합니다.
     *
     * 1. 대기열 승격 정책 : 엄격한 순서, 새치기 없음.
     *      - 남은 자리는 이벤트의 인원수가 아니라 "Inventory 의 실제 잔여 재고"를 기준으로 판단.
     *        이벤트 하나의 인원수만 보면, 여러 번 나눠 취소되며 누적된 자리를 놓쳐서 영원히
     *        승격이 안 되는 버그가 생김.
     * 2. 재고는 즉시 차감하고, 남은 재고로 다음 사람도 들어갈 수 있으면 루프를 돌며 연달아 승격시킵니다.
     */
    public void promoteNext(Long departureId) {
        while (true) {
            // REDIS 에서, 1등 순번의 대기열 id 조회
            Long firstWaitlistId = waitlistRankRepository.firstInLine(departureId);
            if (firstWaitlistId == null) {
                return;
            }

            // DB 에서, 1등 순번의 대기열 조회
            Waitlist waitlist = waitlistRepository.findById(firstWaitlistId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 대기열입니다. id=" + firstWaitlistId));

            // 1등 순번이 원하는 자리수 > 예약취소로 인해 생긴 빈 자리
            if (waitlist.getHeadcount() > inventoryConcurrencyStrategy.getRemainingCount(departureId)) {
                return;
            }

            // 재고 차감, 대기열 승격
            inventoryConcurrencyStrategy.decrease(departureId, waitlist.getHeadcount());
            waitlist.promote();

            // Redis 에서 1등 순번 삭제
            waitlistRankRepository.remove(departureId, firstWaitlistId);
        }
    }

    /**
     * WAITING(자리가 안 나서 계속 대기중)과 PROMOTED(승격됐는데 결제를 안 함) 둘 다 만료 대상.
     * 만료 시점은 Waitlist.expiresAt에 상태별로 미리 계산되어 저장돼있으므로,
     * 여기선 그 값이 지난 후보만 인덱스로 걸러서 가져온다.
     */
    @Scheduled(fixedDelay = 600000) // 10분마다
    public void expireStaleWaitlists() {
        // 만료대상 대기열 전부 조회
        List<Waitlist> targets = waitlistRepository.findByStatusInAndExpiresAtBefore(
                List.of(WaitlistStatus.WAITING, WaitlistStatus.PROMOTED), LocalDateTime.now());

        for (Waitlist waitlist : targets) {
            // 만료시키기
            waitlist.expire();

            // PROMOTED였다면(승격 후 결제 안 함), WAITING이었다면(대기 시간 초과)
            boolean wasPromoted = waitlist.getStatus() == WaitlistStatus.PROMOTED;
            Long departureId = waitlist.getDepartureId();

            if (wasPromoted) {
                // 승격 시점에 이미 Redis에서 빠졌으니, 선점해둔 재고만 반납
                inventoryConcurrencyStrategy.increase(departureId, waitlist.getHeadcount());
            } else {
                // 아직 Redis 대기열에 남아있으니 직접 제거
                waitlistRankRepository.remove(departureId, waitlist.getId());
            }

            // 재고가 늘었든(PROMOTED 만료), 막고 있던 1등이 빠졌든(WAITING 만료) - 다음 대기자 재평가
            promoteNext(departureId);
        }
    }
}
