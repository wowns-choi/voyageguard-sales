package com.voyageguard.sales.application.inventory;

/**
 * Inventory 잔여 재고에 대한 동시성 제어 방식을 감추는 포트(port).
 * 비관적 락/낙관적 락/Redis 원자적 연산 등 구현 방식은 infrastructure/lock 어댑터가 담당한다.
 */
public interface InventoryConcurrencyStrategy {

    int getRemainingCount(Long departureId);

    void decrease(Long departureId, int quantity);

    void increase(Long departureId, int quantity);
}
