package com.sankai.agent.agent;

import com.sankai.agent.agent.model.AgentRequest;
import com.sankai.agent.agent.model.AgentResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * Agent REST API 控制器。
 *
 * <p>提供智能 Agent 的 HTTP 接口，用户可以通过自然语言与 Agent 交互，
 * Agent 会自动选择合适的工具来回答问题。
 *
 * <h3>接口列表：</h3>
 * <table>
 *   <tr><th>方法</th><th>路径</th><th>说明</th></tr>
 *   <tr><td>POST</td><td>/v1/agent/chat</td><td>向 Agent 发送查询</td></tr>
 * </table>
 *
 * <h3>请求示例：</h3>
 * <pre>{@code
 * curl -X POST http://localhost:8080/v1/agent/chat \
 *   -H "Content-Type: application/json" \
 *   -d '{"query": "最近有什么 ERROR 级别的日志？"}'
 * }</pre>
 *
 * <h3>响应示例：</h3>
 * <pre>{@code
 * {
 *   "answer": "最近有 3 条 ERROR 级别的日志...",
 *   "toolCalls": [
 *     {
 *       "tool": "query_logs",
 *       "arguments": {"level": "ERROR", "limit": 10},
 *       "result": {...},
 *       "durationMs": 85,
 *       "success": true
 *     }
 *   ],
 *   "thinking": "用户想查看最近的 ERROR 日志，需要调用 query_logs 工具...",
 *   "success": true
 * }
 * }</pre>
 */
@RestController
@RequestMapping("/v1/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * Agent 对话接口。
     *
     * <p>接收自然语言查询，Agent 自动分析意图、选择工具、执行并返回结果。
     *
     * @param request 包含用户查询的请求体
     * @return Agent 的完整响应（含思考过程、工具调用链、最终回答）
     */
    @PostMapping("/chat")
    public AgentResponse chat(@Valid @RequestBody AgentRequest request) {
        log.info("[Agent API] 收到 chat 请求: {}", request.getQuery());
        long start = System.currentTimeMillis();

        AgentResponse response = agentService.chat(request.getQuery());

        long elapsed = System.currentTimeMillis() - start;
        log.info("[Agent API] 请求处理完成，耗时: {}ms, 成功: {}, 工具调用次数: {}",
                elapsed, response.isSuccess(),
                response.getToolCalls() != null ? response.getToolCalls().size() : 0);

        return response;
    }
}
