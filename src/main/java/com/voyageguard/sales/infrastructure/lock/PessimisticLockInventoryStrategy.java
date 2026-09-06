package com.voyageguard.sales.infrastructure.lock;

import com.voyageguard.sales.application.inventory.InventoryConcurrencyStrategy;
import com.voyageguard.sales.domain.inventory.Inventory;
import com.voyageguard.sales.domain.inventory.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB 비관적 락 기반 어댑터.
 * @Transactional이 필요한 이유: SELECT FOR UPDATE는 활성 트랜잭션 없이는 실행 자체가 안 됨.
 * ReservationService/WaitlistService처럼 이미 트랜잭션 안에서 부르는 호출자는 그냥 합류(REQUIRED)하지만,
 * InventoryController.getRemaining()처럼 트랜잭션 없는 진입점에서 직접 호출되면 여기서 새로 시작해야 함.
 */
@Component
@RequiredArgsConstructor
@Transactional
// inventory.lock-strategy=pessimistic(또는 미설정 시 기본값)일 때만 빈으로 등록
@ConditionalOnProperty(prefix = "inventory", name = "lock-strategy", havingValue = "pessimistic", matchIfMissing = true)
public class PessimisticLockInventoryStrategy implements InventoryConcurrencyStrategy {

    private final InventoryRepository inventoryRepository;

    // Departure id(회차 id) 로 잔여 자리 조회, 비관적 락을 건다.
    @Override
    public int getRemainingCount(Long departureId) {
        return findForUpdate(departureId).getRemainingCount();
    }

    @Override
    public void decrease(Long departureId, int quantity) {
        findForUpdate(departureId) // 비관적 락 걸고, Inventory 조회 후
                .decrease(quantity); // Inventory 감소시키기
    }

    @Override
    public void increase(Long departureId, int quantity) {
        findForUpdate(departureId) // 비관적 락 걸고, Inventory 조회 후
                .increase(quantity); // Inventory 증가시키기
    }

    /** 비관적 락 */
    private Inventory findForUpdate(Long departureId) {
        return inventoryRepository.findByDepartureIdForUpdate(departureId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재고입니다. departureId=" + departureId));
    }
}
