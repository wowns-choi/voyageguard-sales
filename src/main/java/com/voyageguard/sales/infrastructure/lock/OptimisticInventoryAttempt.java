package com.voyageguard.sales.infrastructure.lock;

import com.voyageguard.sales.domain.inventory.Inventory;
import com.voyageguard.sales.domain.inventory.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 낙관적 락 재시도 한 번의 시도 단위.
 * REQUIRES_NEW로 완전히 새 트랜잭션에서 실행해야, 이전 시도가 충돌로 남긴 오염된 영속성 컨텍스트를
 * 안 쓰고 최신 version부터 다시 읽을 수 있다.
 * OptimisticLockInventoryStrategy와 별도 빈으로 분리한 이유는 self-invocation 문제(같은 객체 안에서 부르면 프록시를 안 거쳐 REQUIRES_NEW가 무시됨) 회피.
 */
@Component
@RequiredArgsConstructor
public class OptimisticInventoryAttempt {

    private final InventoryRepository inventoryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decreaseOnce(Long departureId, int quantity) {
        find(departureId) // 재고조회
                .decrease(quantity); // 감소
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increaseOnce(Long departureId, int quantity) {
        find(departureId) // 재고조회
                .increase(quantity); // 증가
    }

    private Inventory find(Long departureId) {
        return inventoryRepository.findByDepartureId(departureId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재고입니다. departureId=" + departureId));
    }
}
