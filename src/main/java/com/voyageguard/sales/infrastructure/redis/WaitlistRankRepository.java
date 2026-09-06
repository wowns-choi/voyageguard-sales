package com.voyageguard.sales.infrastructure.redis;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Departure별 대기 순번을 Redis Sorted Set(ZSET)으로 관리한다.
 * member는 Waitlist id, score는 Unix Epoch
 *
 * - Redis 도입 이유
 * => RDB로는 "내 순번"이 저장된 값이 아니라 매번 COUNT로 계산해야 하는 값이라,
 *    동시에 등록/제거가 일어나는 중에도 정확한 값을 보장하려면 트랜잭션이 필요해 무겁다.
 *    반면에, Redis Sorted Set은 항상 정렬된 상태를 유지하고 있어 ZRANK로 내 위치를 즉시 찾기만 하면 되므로 이 문제가 없다.
 */
@Repository
@RequiredArgsConstructor
public class WaitlistRankRepository {

    private final StringRedisTemplate redisTemplate;

    private String key(Long departureId) {
        return "waitlist:departure:" + departureId;
    }

    public void add(Long departureId, Long waitlistId) {
        redisTemplate.opsForZSet().add(
                key(departureId),
                waitlistId.toString(), // member : waitlist id
                System.currentTimeMillis() // score : Unix Epoch
        );
    }

    /**
     * 1부터 시작하는 순번을 반환한다.
     * Redis Sorted Set에서 이미 제거된 경우(승격/만료 등) null
     * */
    public Long rank(Long departureId, Long waitlistId) {
        Long zeroBasedRank = redisTemplate.opsForZSet().rank(
                key(departureId),
                waitlistId.toString()
        );
        return zeroBasedRank == null ? null : zeroBasedRank + 1;
    }

    /**
     * 대기열 1등(가장 먼저 등록한 사람)의 waitlistId. 대기자가 없으면 null.
     */
    public Long firstInLine(Long departureId) {
        Set<String> firstMember = redisTemplate.opsForZSet().range(
                key(departureId), 0, 0
        );
        if (firstMember == null || firstMember.isEmpty()) {
            return null;
        }
        return Long.valueOf(firstMember.iterator().next());
    }

    /** 특정 회차의 특정 대기자를 대기열에서 제거 (제거 후 뒷사람 순번은 자동으로 당겨짐) */
    public void remove(Long departureId, Long waitlistId) {
        redisTemplate.opsForZSet().remove(key(departureId), waitlistId.toString());
    }

    /**
     * 이 회차에 대기 중인 사람이 한 명이라도 있는지 조회
     */
    public boolean hasWaiting(Long departureId) {
        Long size = redisTemplate.opsForZSet().size(key(departureId));
        return size != null && size > 0;
    }

}
