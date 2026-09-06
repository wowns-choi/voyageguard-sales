package com.voyageguard.sales.infrastructure.redis;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/**
 * Departure별 잔여 재고를 Redis 원자적 카운터로 관리한다 (RedisDecrInventoryStrategy 전용).
 * decrease는 Lua 스크립트로 "확인 후 차감"을 하나의 원자적 단위로 처리한다 - DECRBY 먼저 하고 음수면
 * 되돌리는 방식은, 수량이 제각각인 동시 요청에서 정당한 요청이 남의(실패할) 요청의 되돌리기 전 잠깐의
 * 음수 상태에 걸려 잘못 실패할 수 있어서 채택하지 않았다.
 */
@Repository
@RequiredArgsConstructor
public class InventoryCountRepository {

    private final StringRedisTemplate redisTemplate;

    private String key(Long departureId) {
        return "inventory:departure:" + departureId;
    }

    /** 회차(Departure) 생성 시점에 Redis String(key-value) 에 재고 기록 */
    public void initialize(Long departureId, int totalCapacity) {
        redisTemplate.opsForValue().set(key(departureId), String.valueOf(totalCapacity));
    }

    /** 원자적 확인+차감. 성공하면 차감 후 잔여값(0 이상), 재고 부족이면 -1(값은 안 건드림). */
    public long decrease(Long departureId, int quantity) {
        return redisTemplate.execute(
                DECREASE_SCRIPT,
                List.of(key(departureId)),
                String.valueOf(quantity)
        );
    }
    /**
     * 1. 반환값
     *  - 재고 >= 원하는 자리수 : 차감 후 잔여 재고 반환 (0 이상)
     *  - 재고부족시 : -1 반환 (값은 안 건드림)
     *  - 키 없으면 : -2 반환 (initialize 누락 등 비정상 상황)
     *
     * 2. 루아스크립트 채택 이유
     *  - "재고 확인"과 "차감"을 원자적으로 묶기 위함.
     *  - 왜 묶었냐면, 안 묶으면(DECRBY 먼저 하고 음수면 되돌리는 방식) 다음 문제가 있음:
     *      잔여 재고 3, A는 5자리를 원해서 DECRBY -> -2(실패, 되돌려야 함).
     *      A가 되돌리기(INCRBY) 전에 B(3자리를 원함, 정상적으로 성공했어야 함)가 DECRBY 하면
     *      -2에서 또 깎여서 B도 음수를 받아 잘못 실패 처리됨 - A의 실패가 B에게 전염됨.
     *  - 루아스크립트는 "확인 후 차감"을 하나의 끊기지 않는 동작으로 처리해서, A는 확인 단계에서
     *    걸러져 애초에 값을 건드리지 않고 실패하므로, B는 A의 영향을 전혀 안 받고 정상 판정됨.
     * */
    private static final RedisScript<Long> DECREASE_SCRIPT = RedisScript.of(
            // - redis.call('GET', KEYS[1]) 에서 KEYS 라는 배열은 List.of(key(departureId)) 를 의미.
            //   따라서, redis.call('GET', key(departureId)) 가 되는 것임.
            // - String.valueOf(quantity) 는 ARGV[1] 자리로 들어감.
            // - tonumber(문자열) : 문자열 -> 숫자
            """
            local remaining = tonumber(redis.call('GET', KEYS[1]))
            if remaining == nil then
                return -2
            end
            local quantity = tonumber(ARGV[1])
            if remaining >= quantity then
                return redis.call('DECRBY', KEYS[1], quantity)
            else
                return -1
            end
            """,
            Long.class
    );

    public void increase(Long departureId, int quantity) {
        redisTemplate.opsForValue().increment(key(departureId), quantity);
    }

    public int getRemainingCount(Long departureId) {
        String value = redisTemplate.opsForValue().get(key(departureId));
        if (value == null) {
            throw new IllegalArgumentException("존재하지 않는 재고입니다. departureId=" + departureId);
        }
        return Integer.parseInt(value);
    }
}
