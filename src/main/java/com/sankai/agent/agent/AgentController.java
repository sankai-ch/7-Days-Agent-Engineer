package com.sankai.agent.agent;

import com.sankai.agent.agent.model.AgentRequest;
import com.sankai.agent.agent.model.AgentResponse;
import com.sankai.agent.security.SecurityChain;
import com.sankai.agent.security.SecurityChain.SecurityCheckResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * Agent REST API 控制器。
 *
 * <p>Day 6 增加安全检查链：限流 → 熔断 → 幂等 → 注入检测 → 脱敏。
 *
 * <h3>请求示例：</h3>
 * <pre>{@code
 * curl -X POST http://localhost:8080/v1/agent/chat \
 *   -H "Content-Type: application/json" \
 *   -d '{"query": "最近有什么 ERROR 级别的日志？"}'
 * }</pre>
 */
@RestController
@RequestMapping("/v1/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentService agentService;
    private final SecurityChain securityChain;

    public AgentController(AgentService agentService, SecurityChain securityChain) {
        this.agentService = agentService;
        this.securityChain = securityChain;
    }

    /**
     * Agent 对话接口（含安全检查链）。
     */
    @PostMapping("/chat")
    public AgentResponse chat(@Valid @RequestBody AgentRequest request,
                               HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getRemoteAddr();
        log.info("[Agent API] 收到请求, IP={}", clientIp);

        // ===== Day 6: 前置安全检查 =====
        SecurityCheckResult checkResult = securityChain.preCheck(request.getQuery(), clientIp);

        if (!checkResult.isAllowed()) {
            log.warn("[Agent API] 安全检查拦截: rule={}, reason={}", checkResult.getRuleId(), checkResult.getReason());
            return AgentResponse.failure(checkResult.getReason());
        }

        if (checkResult.isFromCache()) {
            log.info("[Agent API] 幂等缓存命中");
            return AgentResponse.success(checkResult.getCachedAnswer(), java.util.List.of(), "幂等缓存命中");
        }

        // ===== 执行业务逻辑（使用脱敏后的输入） =====
        long start = System.currentTimeMillis();
        AgentResponse response = agentService.chat(checkResult.getSanitizedInput());
        long elapsed = System.currentTimeMillis() - start;

        // ===== Day 6: 后置安全处理 =====
        String sanitizedAnswer = securityChain.postProcess(
                response.getAnswer(), response.isSuccess(), checkResult.getIdempotencyKey());
        response.setAnswer(sanitizedAnswer);

        log.info("[Agent API] 完成, 耗时={}ms, 成功={}", elapsed, response.isSuccess());
        return response;
    }
}
