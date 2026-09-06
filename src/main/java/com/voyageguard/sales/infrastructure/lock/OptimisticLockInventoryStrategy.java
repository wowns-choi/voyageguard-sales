package com.voyageguard.sales.infrastructure.lock;

import com.voyageguard.sales.application.inventory.InventoryConcurrencyStrategy;
import com.voyageguard.sales.domain.inventory.Inventory;
import com.voyageguard.sales.domain.inventory.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/** DB 낙관적 락(@Version) 기반 어댑터. 충돌 시 OptimisticInventoryAttempt를 통해 새 트랜잭션에서 재시도. */
@Component
@RequiredArgsConstructor
// inventory.lock-strategy=optimistic 일 때만 빈으로 등록
@ConditionalOnProperty(prefix = "inventory", name = "lock-strategy", havingValue = "optimistic")
public class OptimisticLockInventoryStrategy implements InventoryConcurrencyStrategy {

    private static final int MAX_ATTEMPTS = 5;

    private final InventoryRepository inventoryRepository;
    private final OptimisticInventoryAttempt attempt;

    // Departure id(회차 id) 로 잔여 자리 조회, 락 걸지 않는다.
    @Override
    public int getRemainingCount(Long departureId) {
        return findByDepartureId(departureId).getRemainingCount();
    }
    private Inventory findByDepartureId(Long departureId) {
        return inventoryRepository.findByDepartureId(departureId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재고입니다. departureId=" + departureId));
    }

    @Override
    public void decrease(Long departureId, int quantity) {
        retry(() -> attempt.decreaseOnce(departureId, quantity));
    }

    @Override
    public void increase(Long departureId, int quantity) {
        retry(() -> attempt.increaseOnce(departureId, quantity));
    }

    // ObjectOptimisticLockingFailureException(버전 충돌)만 재시도 대상으로 함.
    // InsufficientInventoryException(진짜 재고부족)은 여기서 안 잡히고 그대로 호출자에게 전파됨(재시도해도 해결 안 되는 진짜 실패라서).
    private void retry(Runnable oneAttempt) {
        ObjectOptimisticLockingFailureException lastFailure = null;
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            try {
                oneAttempt.run();
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                lastFailure = e;
            }
        }
        throw lastFailure;
    }
}
