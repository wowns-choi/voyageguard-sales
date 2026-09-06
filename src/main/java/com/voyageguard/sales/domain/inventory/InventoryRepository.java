package com.voyageguard.sales.domain.inventory;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    // 낙관적 락 전략용 - 락 없이 조회, 대신 Inventory.version으로 UPDATE 시점에 충돌 검사
    Optional<Inventory> findByDepartureId(Long departureId);

    // 비관적 락 전략용
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.departureId = :departureId")
    Optional<Inventory> findByDepartureIdForUpdate(@Param("departureId") Long departureId);
}
