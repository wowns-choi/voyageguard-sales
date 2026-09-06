package com.voyageguard.sales.infrastructure.lock;

import com.voyageguard.sales.application.inventory.InventoryConcurrencyStrategy;
import com.voyageguard.sales.domain.inventory.InsufficientInventoryException;
import com.voyageguard.sales.infrastructure.redis.InventoryCountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Redis 원자적 연산(Lua 스크립트) 기반 어댑터. DB 락 없이 Redis 싱글스레드 처리로 경쟁을 없앤다.
 * 실험용 전략 - 활성화 중엔 Inventory.remainingCount(DB 컬럼)는 갱신되지 않고 Redis 값만 신뢰한다.
 * 실제 채택 시엔 DB 동기화(Outbox 등) 설계가 별도로 필요함.
 */
@Component
@RequiredArgsConstructor
// inventory.lock-strategy=redis-decr 일 때만 빈으로 등록
@ConditionalOnProperty(prefix = "inventory", name = "lock-strategy", havingValue = "redis-decr")
public class RedisDecrInventoryStrategy implements InventoryConcurrencyStrategy {

    private final InventoryCountRepository inventoryCountRepository;

    /** Redis 에서, Departure id(회차 id) 로 잔여 자리 조회 */
    @Override
    public int getRemainingCount(Long departureId) {
        return inventoryCountRepository.getRemainingCount(departureId);
    }

    @Override
    public void decrease(Long departureId, int quantity) {
        long result = inventoryCountRepository.decrease(departureId, quantity);
        if (result == -1) {
            throw new InsufficientInventoryException("잔여 재고가 부족합니다. 요청: " + quantity);
        }
        if (result == -2) {
            throw new IllegalArgumentException("존재하지 않는 재고입니다. departureId=" + departureId);
        }
    }

    @Override
    public void increase(Long departureId, int quantity) {
        inventoryCountRepository.increase(departureId, quantity);
    }
}
