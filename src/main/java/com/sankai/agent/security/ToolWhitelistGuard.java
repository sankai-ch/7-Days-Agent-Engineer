package com.sankai.agent.security;

import com.sankai.agent.mcp.server.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工具白名单与调用限流守卫。
 *
 * <p>提供两层安全控制：
 * <ol>
 *   <li><b>白名单校验</b>：只允许已注册的合法工具被调用，
 *       防止 LLM 被注入后尝试调用不存在或未授权的工具</li>
 *   <li><b>调用限流</b>：限制单个 IP/会话在时间窗口内的请求次数，
 *       防止恶意刷接口或 DoS 攻击</li>
 * </ol>
 *
 * <h3>配置（application.yml）：</h3>
 * <pre>{@code
 * security:
 *   rate-limit:
 *     max-requests-per-minute: 30
 * }</pre>
 */
@Component
public class ToolWhitelistGuard {

    private static final Logger log = LoggerFactory.getLogger(ToolWhitelistGuard.class);

    private final McpToolRegistry toolRegistry;

    /** 每分钟最大请求数 */
    @Value("${security.rate-limit.max-requests-per-minute:30}")
    private int maxRequestsPerMinute = 30;

    /** 限流计数器：key -> [count, windowStart] */
    private final ConcurrentHashMap<String, long[]> rateLimitCounters = new ConcurrentHashMap<>();

    public ToolWhitelistGuard(McpToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 校验工具名称是否在白名单中。
     *
     * @param toolName 工具名称
     * @return true 如果工具合法
     */
    public boolean isToolAllowed(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            log.warn("[安全] 拒绝空工具名称调用");
            return false;
        }

        boolean allowed = toolRegistry.findTool(toolName).isPresent();
        if (!allowed) {
            log.warn("[安全] 拒绝未授权工具调用: {}", toolName);
        }
        return allowed;
    }

    /**
     * 获取当前允许的工具列表（即白名单）。
     *
     * @return 合法工具名称集合
     */
    public Set<String> getAllowedTools() {
        return toolRegistry.getToolNames();
    }

    /**
     * 检查请求是否被限流。
     *
     * <p>使用滑动窗口算法（1 分钟窗口），超过阈值则拒绝。
     *
     * @param clientId 客户端标识（IP 或会话 ID）
     * @return true 如果允许请求通过，false 如果被限流
     */
    public boolean checkRateLimit(String clientId) {
        long now = System.currentTimeMillis();
        long windowMs = 60_000; // 1 分钟

        long[] counter = rateLimitCounters.compute(clientId, (key, existing) -> {
            if (existing == null || now - existing[1] > windowMs) {
                // 新窗口
                return new long[]{1, now};
            } else {
                existing[0]++;
                return existing;
            }
        });

        if (counter[0] > maxRequestsPerMinute) {
            log.warn("[安全] 限流触发: clientId={}, count={}/{}", clientId, counter[0], maxRequestsPerMinute);
            return false;
        }
        return true;
    }

    /**
     * 校验工具调用参数的安全性。
     *
     * <p>检查参数中是否包含 SQL 注入、路径遍历等危险内容。
     *
     * @param arguments 工具参数
     * @return 校验结果
     */
    public ParameterCheckResult validateParameters(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return ParameterCheckResult.safe();
        }

        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            String value = String.valueOf(entry.getValue());

            // 检查 SQL 注入
            if (value.matches("(?i).*('\\s*(OR|AND)\\s+'|;\\s*(DROP|DELETE|UPDATE|INSERT|ALTER)\\b|--.*).*")) {
                log.warn("[安全] 参数 SQL 注入检测: key={}, value={}", entry.getKey(), value);
                return ParameterCheckResult.blocked("SQL_INJECTION",
                        "参数 " + entry.getKey() + " 包含危险的 SQL 片段");
            }

            // 检查路径遍历
            if (value.contains("../") || value.contains("..\\")) {
                log.warn("[安全] 参数路径遍历检测: key={}", entry.getKey());
                return ParameterCheckResult.blocked("PATH_TRAVERSAL",
                        "参数 " + entry.getKey() + " 包含路径遍历");
            }

            // 检查过长参数值
            if (value.length() > 1000) {
                log.warn("[安全] 参数过长: key={}, length={}", entry.getKey(), value.length());
                return ParameterCheckResult.blocked("PARAM_TOO_LONG",
                        "参数 " + entry.getKey() + " 长度超过限制");
            }
        }

        return ParameterCheckResult.safe();
    }

    /** 参数校验结果 */
    public static class ParameterCheckResult {
        private final boolean safe;
        private final String ruleId;
        private final String reason;

        private ParameterCheckResult(boolean safe, String ruleId, String reason) {
            this.safe = safe;
            this.ruleId = ruleId;
            this.reason = reason;
        }

        public static ParameterCheckResult safe() {
            return new ParameterCheckResult(true, null, null);
        }

        public static ParameterCheckResult blocked(String ruleId, String reason) {
            return new ParameterCheckResult(false, ruleId, reason);
        }

        public boolean isSafe() { return safe; }
        public String getRuleId() { return ruleId; }
        public String getReason() { return reason; }
    }
}
