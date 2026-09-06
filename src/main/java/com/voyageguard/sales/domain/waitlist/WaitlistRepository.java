package com.voyageguard.sales.domain.waitlist;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitlistRepository extends JpaRepository<Waitlist, Long> {

    // status+expiresAt 복합 인덱스를 타는 인덱스 레인지 스캔 - 만료 후보만 걸러서 가져온다.
    List<Waitlist> findByStatusInAndExpiresAtBefore(List<WaitlistStatus> statuses, LocalDateTime expiresAt);
}
