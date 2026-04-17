package com.sankai.agent.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 熔断器 + 优雅降级 + 幂等控制。
 *
 * <p>提供三层稳定性保障：
 * <ol>
 *   <li><b>熔断器（Circuit Breaker）</b>：连续失败超过阈值时自动熔断，
 *       阻止请求继续打到已经异常的下游服务</li>
 *   <li><b>优雅降级</b>：熔断后返回预设的降级回答，而不是抛异常</li>
 *   <li><b>幂等控制</b>：基于请求内容的幂等键，防止重复请求造成重复计费</li>
 * </ol>
 *
 * <h3>熔断状态机：</h3>
 * <pre>{@code
 *                  失败次数 >= 阈值
 *  [CLOSED] ──────────────────────> [OPEN]
 *     ▲                               │
 *     │                               │ 等待冷却时间
 *     │       成功                    ▼
 *     └──────────────────────── [HALF_OPEN]
 *                                     │
 *                     失败            │
 *                     └───> [OPEN] <──┘
 * }</pre>
 */
@Component
public class ResilienceGuard {

    private static final Logger log = LoggerFactory.getLogger(ResilienceGuard.class);

    /** 连续失败几次后熔断 */
    @Value("${security.circuit-breaker.failure-threshold:5}")
    private int failureThreshold = 5;

    /** 熔断后的冷却时间（毫秒） */
    @Value("${security.circuit-breaker.cooldown-ms:30000}")
    private long cooldownMs = 30000;

    /** 降级回答 */
    private static final String FALLBACK_ANSWER = "抱歉，系统当前负载较高，暂时无法处理您的请求。请稍后重试。";

    // ==================== 熔断器状态 ====================

    /** 连续失败计数 */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /** 熔断器状态 */
    private volatile CircuitState state = CircuitState.CLOSED;

    /** 上次熔断时间 */
    private volatile long lastOpenTime = 0;

    // ==================== 幂等键缓存 ====================

    /** 幂等缓存：requestHash -> [result, timestamp] */
    private final ConcurrentHashMap<String, CachedResult> idempotencyCache = new ConcurrentHashMap<>();

    /** 幂等缓存过期时间（毫秒） */
    @Value("${security.idempotency.cache-ttl-ms:60000}")
    private long idempotencyCacheTtlMs = 60000;

    // ==================== 熔断器方法 ====================

    /**
     * 检查熔断器是否允许通过。
     *
     * @return true 允许请求通过，false 被熔断
     */
    public boolean isCircuitAllowed() {
        switch (state) {
            case CLOSED:
                return true;
            case OPEN:
                // 检查是否已过冷却期
                if (System.currentTimeMillis() - lastOpenTime > cooldownMs) {
                    state = CircuitState.HALF_OPEN;
                    log.info("[熔断器] 冷却期结束，进入 HALF_OPEN 状态");
                    return true; // 允许一个探测请求
                }
                log.warn("[熔断器] 熔断中，拒绝请求（剩余冷却 {}ms）",
                        cooldownMs - (System.currentTimeMillis() - lastOpenTime));
                return false;
            case HALF_OPEN:
                return true; // 允许探测
            default:
                return true;
        }
    }

    /**
     * 记录一次成功调用。
     */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        if (state == CircuitState.HALF_OPEN) {
            state = CircuitState.CLOSED;
            log.info("[熔断器] 探测成功，恢复到 CLOSED 状态");
        }
    }

    /**
     * 记录一次失败调用。
     */
    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (state == CircuitState.HALF_OPEN) {
            // 探测失败，重新熔断
            state = CircuitState.OPEN;
            lastOpenTime = System.currentTimeMillis();
            log.warn("[熔断器] HALF_OPEN 探测失败，重新熔断");
        } else if (failures >= failureThreshold && state == CircuitState.CLOSED) {
            state = CircuitState.OPEN;
            lastOpenTime = System.currentTimeMillis();
            log.warn("[熔断器] 连续失败 {} 次，触发熔断！", failures);
        }
    }

    /**
     * 获取降级回答。
     */
    public String getFallbackAnswer() {
        return FALLBACK_ANSWER;
    }

    /**
     * 获取当前熔断器状态。
     */
    public String getCircuitState() {
        return state.name();
    }

    // ==================== 幂等控制 ====================

    /**
     * 生成幂等键。
     *
     * @param input 用户输入
     * @return 幂等键（基于内容 hash）
     */
    public String generateIdempotencyKey(String input) {
        if (input == null) return "null";
        return "req-" + Integer.toHexString(input.trim().hashCode());
    }

    /**
     * 检查是否存在缓存的幂等结果。
     *
     * @param key 幂等键
     * @return 缓存的结果，或 null
     */
    public String getCachedResult(String key) {
        CachedResult cached = idempotencyCache.get(key);
        if (cached == null) return null;

        // 检查是否过期
        if (System.currentTimeMillis() - cached.timestamp > idempotencyCacheTtlMs) {
            idempotencyCache.remove(key);
            return null;
        }
        log.info("[幂等] 命中缓存: key={}", key);
        return cached.result;
    }

    /**
     * 缓存幂等结果。
     */
    public void cacheResult(String key, String result) {
        idempotencyCache.put(key, new CachedResult(result, System.currentTimeMillis()));
        // 清理过期条目
        long now = System.currentTimeMillis();
        idempotencyCache.entrySet().removeIf(e -> now - e.getValue().timestamp > idempotencyCacheTtlMs);
    }

    // ==================== 内部类 ====================

    /** 熔断器状态枚举 */
    public enum CircuitState {
        /** 正常：允许所有请求 */
        CLOSED,
        /** 熔断：拒绝所有请求 */
        OPEN,
        /** 半开：允许探测请求 */
        HALF_OPEN
    }

    /** 幂等缓存条目 */
    private record CachedResult(String result, long timestamp) {}
}
