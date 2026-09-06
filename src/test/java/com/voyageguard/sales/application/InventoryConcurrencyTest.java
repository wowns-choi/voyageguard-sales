package com.voyageguard.sales.application;


import com.voyageguard.sales.application.inventory.InventoryConcurrencyStrategy;
import com.voyageguard.sales.domain.inventory.Inventory;
import com.voyageguard.sales.domain.inventory.InsufficientInventoryException;
import com.voyageguard.sales.domain.inventory.InventoryRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@SpringBootTest
class InventoryConcurrencyTest {

    @Autowired
    private InventoryConcurrencyStrategy inventoryConcurrencyStrategy;

    @Autowired
    private InventoryRepository inventoryRepository;

    /**
     * 동시성
     * - 락 없는 Inventory.decrease()의 초과판매 재현 테스트.
     * - "재고 확인"과 "재고 차감" 사이에 다른 스레드가 끼어들 수 있어(Check-Then-Act 레이스 컨디션),
     * - 재고 1개에 10명이 동시에 요청해도 여러 명이 동시에 통과해버린다.
     * - 락을 적용하면 이 테스트가 통과해야 한다.
     */
    @Test
    void 재고가_1개뿐인_상품에_10개_요청이_동시에_들어와도_재고는_음수가_되지_않는다() throws
            InterruptedException {
        Inventory inventory = inventoryRepository.save(Inventory.create(999L, 1));
        int threadCount = 10;

        // 고객 10명이 동시에 예약을 시도하는 상황을 재현하기 위한 스레드풀
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        // 10개 요청이 모두 끝난 뒤에 결과를 확인하기 위한 카운터
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    inventoryConcurrencyStrategy.decrease(inventory.getDepartureId(), 1);
                    successCount.incrementAndGet();
                } catch (InsufficientInventoryException e) {
                    // 재고 부족 - 정상적으로 막힌 경우이므로 무시
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertTrue(successCount.get() <= 1, "재고는 1개인데 " + successCount.get() + "명이 동시에 성공했습니다 (초과판매)");
    }

}
