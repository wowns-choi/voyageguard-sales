package com.voyageguard.sales.infrastructure.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Sales 전용 Outbox - 도메인 상태 변경과 "이 이벤트를 나중에 Kafka로 발행하겠다"는 의도를
 * 같은 DB 트랜잭션으로 묶기 위한 저장 단위. 발행 자체(Kafka로 실제 전송)는 별도 릴레이가 이
 * 테이블을 폴링해서 처리한다.
 * BC마다 하나씩 갖는 이유: Outbox 패턴의 전제가 "도메인 변경과 같은 DB, 같은 트랜잭션"인데,
 * DB를 BC별로 나누면서 Sales/Payment가 공유하던 단일 OutboxEvent 테이블이 그 전제를 더 이상
 * 만족 못 하게 됨 - 그래서 BC마다 쪼갬(Payment는 PaymentOutboxEvent 참고).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventType; // 예: "ReservationCancelled", 로그 디버깅 필터링용

    private String topic; // 예: "reservation.cancelled"

    private String messageKey; // Kafka 파티션 키로 쓸 값 (예: departureId)

    @Lob
    private String payload; // JSON 문자열

    private boolean published;

    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    private SalesOutboxEvent(String eventType, String topic, String messageKey, String payload) {
        this.eventType = eventType;
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.published = false;
        this.createdAt = LocalDateTime.now();
    }

    public static SalesOutboxEvent create(String eventType, String topic, String messageKey, String payload) {
        return new SalesOutboxEvent(eventType, topic, messageKey, payload);
    }

    public void markPublished() {
        if (published) {
            throw new IllegalStateException("이미 발행된 이벤트입니다. id=" + id);
        }
        this.published = true;
        this.publishedAt = LocalDateTime.now();
    }
}
