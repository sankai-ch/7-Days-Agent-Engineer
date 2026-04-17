package com.sankai.agent.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 安全检查链 —— 统一入口，串联所有安全组件。
 *
 * <p>对每个请求按顺序执行以下检查：
 * <ol>
 *   <li>限流检查（{@link ToolWhitelistGuard#checkRateLimit}）</li>
 *   <li>熔断器检查（{@link ResilienceGuard#isCircuitAllowed}）</li>
 *   <li>幂等检查（{@link ResilienceGuard#getCachedResult}）</li>
 *   <li>Prompt Injection 检测（{@link PromptInjectionGuard#check}）</li>
 *   <li>敏感信息脱敏（{@link SensitiveDataMasker#mask}）</li>
 * </ol>
 *
 * <h3>调用示例（在 Controller 中）：</h3>
 * <pre>{@code
 * SecurityCheckResult result = securityChain.preCheck(query, clientIp);
 * if (!result.isAllowed()) {
 *     return AgentResponse.failure(result.getReason());
 * }
 * String sanitizedQuery = result.getSanitizedInput();
 * }</pre>
 */
@Component
public class SecurityChain {

    private static final Logger log = LoggerFactory.getLogger(SecurityChain.class);

    private final PromptInjectionGuard injectionGuard;
    private final SensitiveDataMasker dataMasker;
    private final ToolWhitelistGuard whitelistGuard;
    private final ResilienceGuard resilienceGuard;

    public SecurityChain(PromptInjectionGuard injectionGuard,
                          SensitiveDataMasker dataMasker,
                          ToolWhitelistGuard whitelistGuard,
                          ResilienceGuard resilienceGuard) {
        this.injectionGuard = injectionGuard;
        this.dataMasker = dataMasker;
        this.whitelistGuard = whitelistGuard;
        this.resilienceGuard = resilienceGuard;
    }

    /**
     * 请求前置安全检查。
     *
     * @param input    用户原始输入
     * @param clientId 客户端标识（IP 或会话 ID）
     * @return 检查结果（含脱敏后的输入）
     */
    public SecurityCheckResult preCheck(String input, String clientId) {
        log.debug("[安全链] 开始前置检查, clientId={}", clientId);

        // 1. 限流
        if (!whitelistGuard.checkRateLimit(clientId)) {
            return SecurityCheckResult.blocked("RATE_LIMITED",
                    "请求过于频繁，请稍后重试（限制: 每分钟 30 次）");
        }

        // 2. 熔断器
        if (!resilienceGuard.isCircuitAllowed()) {
            return SecurityCheckResult.blocked("CIRCUIT_OPEN",
                    resilienceGuard.getFallbackAnswer());
        }

        // 3. 幂等检查
        String idempotencyKey = resilienceGuard.generateIdempotencyKey(input);
        String cached = resilienceGuard.getCachedResult(idempotencyKey);
        if (cached != null) {
            return SecurityCheckResult.cached(idempotencyKey, cached);
        }

        // 4. Prompt Injection 检测
        PromptInjectionGuard.InjectionCheckResult injectionResult = injectionGuard.check(input);
        if (!injectionResult.isSafe()) {
            return SecurityCheckResult.blocked("INJECTION_" + injectionResult.getRuleId(),
                    injectionResult.getReason());
        }

        // 5. 敏感信息脱敏
        String sanitized = dataMasker.mask(input);

        log.debug("[安全链] 前置检查通过, idempotencyKey={}", idempotencyKey);
        return SecurityCheckResult.allowed(sanitized, idempotencyKey);
    }

    /**
     * 请求后置处理：对输出脱敏 + 记录熔断状态 + 缓存幂等结果。
     *
     * @param output         LLM 输出
     * @param success        是否成功
     * @param idempotencyKey 幂等键
     * @return 脱敏后的输出
     */
    public String postProcess(String output, boolean success, String idempotencyKey) {
        // 记录熔断状态
        if (success) {
            resilienceGuard.recordSuccess();
        } else {
            resilienceGuard.recordFailure();
        }

        // 输出脱敏
        String sanitizedOutput = dataMasker.mask(output);

        // 幂等缓存
        if (success && idempotencyKey != null) {
            resilienceGuard.cacheResult(idempotencyKey, sanitizedOutput);
        }

        return sanitizedOutput;
    }

    /**
     * 获取当前安全状态概览。
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("circuitState", resilienceGuard.getCircuitState());
        status.put("allowedTools", whitelistGuard.getAllowedTools());
        return status;
    }

    // ==================== 检查结果 ====================

    /**
     * 安全检查结果。
     */
    public static class SecurityCheckResult {
        private final boolean allowed;
        private final String sanitizedInput;
        private final String idempotencyKey;
        private final String reason;
        private final String ruleId;
        private final boolean fromCache;
        private final String cachedAnswer;

        private SecurityCheckResult(boolean allowed, String sanitizedInput, String idempotencyKey,
                                     String reason, String ruleId, boolean fromCache, String cachedAnswer) {
            this.allowed = allowed;
            this.sanitizedInput = sanitizedInput;
            this.idempotencyKey = idempotencyKey;
            this.reason = reason;
            this.ruleId = ruleId;
            this.fromCache = fromCache;
            this.cachedAnswer = cachedAnswer;
        }

        public static SecurityCheckResult allowed(String sanitizedInput, String idempotencyKey) {
            return new SecurityCheckResult(true, sanitizedInput, idempotencyKey,
                    null, null, false, null);
        }

        public static SecurityCheckResult blocked(String ruleId, String reason) {
            return new SecurityCheckResult(false, null, null, reason, ruleId, false, null);
        }

        public static SecurityCheckResult cached(String idempotencyKey, String cachedAnswer) {
            return new SecurityCheckResult(true, null, idempotencyKey,
                    null, null, true, cachedAnswer);
        }

        public boolean isAllowed() { return allowed; }
        public String getSanitizedInput() { return sanitizedInput; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public String getReason() { return reason; }
        public String getRuleId() { return ruleId; }
        public boolean isFromCache() { return fromCache; }
        public String getCachedAnswer() { return cachedAnswer; }
    }
}
