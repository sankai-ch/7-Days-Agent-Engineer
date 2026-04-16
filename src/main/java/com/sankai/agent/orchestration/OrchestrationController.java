package com.sankai.agent.orchestration;

import com.sankai.agent.agent.model.AgentRequest;
import com.sankai.agent.orchestration.model.OrchestrationResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 编排流水线 REST API 控制器。
 *
 * <p>提供 Planner→Executor→Reviewer→汇总 四阶段编排的 HTTP 接口。
 * 与 Day 3 的 {@code /v1/agent/chat}（单 Agent）并存，用户可选择使用哪种模式。
 *
 * <h3>接口对比：</h3>
 * <table>
 *   <tr><th>接口</th><th>模式</th><th>适用场景</th></tr>
 *   <tr><td>POST /v1/agent/chat</td><td>单 Agent (Day3)</td><td>简单查询</td></tr>
 *   <tr><td>POST /v1/orchestrate</td><td>三角色编排 (Day4)</td><td>复杂分析任务</td></tr>
 * </table>
 *
 * <h3>请求示例：</h3>
 * <pre>{@code
 * curl -X POST http://localhost:8080/v1/orchestrate \
 *   -H "Content-Type: application/json" \
 *   -d '{"query": "分析系统当前的健康状况，包括错误日志和未解决的高优工单"}'
 * }</pre>
 */
@RestController
@RequestMapping("/v1")
public class OrchestrationController {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationController.class);

    private final OrchestrationService orchestrationService;

    public OrchestrationController(OrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    /**
     * 编排流水线接口 —— 三角色协作模式。
     *
     * <p>接收自然语言任务，经 Planner/Executor/Reviewer 三角色协作后，
     * 返回包含完整链路信息的结构化响应。
     *
     * @param request 包含用户查询的请求体（复用 Day 3 的 AgentRequest）
     * @return 编排流水线的完整响应
     */
    @PostMapping("/orchestrate")
    public OrchestrationResponse orchestrate(@Valid @RequestBody AgentRequest request) {
        log.info("[Orchestrate API] 收到编排请求: {}", request.getQuery());
        return orchestrationService.orchestrate(request.getQuery());
    }
}
