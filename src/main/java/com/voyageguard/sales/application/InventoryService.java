package com.voyageguard.sales.application;

import com.voyageguard.sales.domain.inventory.Inventory;
import com.voyageguard.sales.domain.inventory.InventoryRepository;
import com.voyageguard.sales.infrastructure.redis.InventoryCountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final InventoryCountRepository inventoryCountRepository;

    // capacity를 파라미터로 받는 이유: 여기서 DepartureRepository를 직접 조회하면 Planning-Sales 순환 의존이 생김.
    public Long create(Long departureId, Integer capacity) {
        Inventory inventory = Inventory.create(departureId, capacity);
        Long id = inventoryRepository.save(inventory).getId();

        // Redis DECR 전략이 나중에 활성화되더라도 바로 쓸 수 있도록, 현재 활성 전략과 무관하게 항상 초기화해둔다.
        inventoryCountRepository.initialize(departureId, capacity);

        return id;
    }

}
