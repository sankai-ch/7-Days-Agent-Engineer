package com.sankai.agent.orchestration;

import com.sankai.agent.agent.model.AgentRequest;
import com.sankai.agent.orchestration.model.OrchestrationResponse;
import com.sankai.agent.orchestration.model.ReviewVerdict;
import com.sankai.agent.security.SecurityChain;
import com.sankai.agent.security.SecurityChain.SecurityCheckResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 编排流水线 REST API 控制器。
 *
 * <p>Day 6 增加安全检查链：限流 → 熔断 → 幂等 → 注入检测 → 脱敏。
 *
 * <h3>请求示例：</h3>
 * <pre>{@code
 * curl -X POST http://localhost:8080/v1/orchestrate \
 *   -H "Content-Type: application/json" \
 *   -d '{"query": "分析系统当前的健康状况"}'
 * }</pre>
 */
@RestController
@RequestMapping("/v1")
public class OrchestrationController {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationController.class);

    private final OrchestrationService orchestrationService;
    private final SecurityChain securityChain;

    public OrchestrationController(OrchestrationService orchestrationService,
                                    SecurityChain securityChain) {
        this.orchestrationService = orchestrationService;
        this.securityChain = securityChain;
    }

    /**
     * 编排流水线接口（含安全检查链）。
     */
    @PostMapping("/orchestrate")
    public OrchestrationResponse orchestrate(@Valid @RequestBody AgentRequest request,
                                              HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getRemoteAddr();
        log.info("[Orchestrate API] 收到请求, IP={}", clientIp);

        // ===== Day 6: 前置安全检查 =====
        SecurityCheckResult checkResult = securityChain.preCheck(request.getQuery(), clientIp);

        if (!checkResult.isAllowed()) {
            log.warn("[Orchestrate API] 安全拦截: rule={}", checkResult.getRuleId());
            return OrchestrationResponse.failure(checkResult.getReason(), 0);
        }

        if (checkResult.isFromCache()) {
            log.info("[Orchestrate API] 幂等缓存命中");
            return OrchestrationResponse.success(
                    checkResult.getCachedAnswer(), List.of(), List.of(),
                    ReviewVerdict.pass("幂等缓存命中", 100), 0);
        }

        // ===== 执行业务逻辑 =====
        OrchestrationResponse response = orchestrationService.orchestrate(checkResult.getSanitizedInput());

        // ===== Day 6: 后置安全处理 =====
        String sanitizedAnswer = securityChain.postProcess(
                response.getAnswer(), response.isSuccess(), checkResult.getIdempotencyKey());
        response.setAnswer(sanitizedAnswer);

        return response;
    }

    /**
     * 安全状态查询接口。
     */
    @GetMapping("/security/status")
    public Object securityStatus() {
        return securityChain.getStatus();
    }
}
