package com.voyageguard.sales.infrastructure.outbox;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SalesOutboxEvent 테이블을 주기적으로 폴링해서, 아직 발행 안 된 행을 실제로 Kafka에 발행하는 릴레이.
 * 이벤트 하나가 발행 실패해도(카프카 장애 등) 나머지 이벤트 처리에 영향 없게 개별적으로 처리하고,
 * 실패한 건 published=false로 남겨둬서 다음 폴링 때 자동 재시도된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesOutboxRelay {

    private final SalesOutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void relay() {
        List<SalesOutboxEvent> pendingEvents = outboxEventRepository.findByPublishedFalse();
        for (SalesOutboxEvent event : pendingEvents) {
            try {
                // .get() 을 붙여, 실제로 Kafka에 보내고, 성공 확인까지 기다림
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                        .get();
                event.markPublished();
            } catch (Exception e) {
                log.warn("SalesOutboxEvent 발행 실패, 다음 폴링에서 재시도. id={}, topic={}", event.getId(), event.getTopic(), e);
            }
        }
    }
}
