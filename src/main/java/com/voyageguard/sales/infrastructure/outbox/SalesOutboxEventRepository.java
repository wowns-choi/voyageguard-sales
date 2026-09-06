package com.voyageguard.sales.infrastructure.outbox;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOutboxEventRepository extends JpaRepository<SalesOutboxEvent, Long> {

    List<SalesOutboxEvent> findByPublishedFalse();
}
