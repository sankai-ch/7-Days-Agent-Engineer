package com.sankai.agent.orchestration.role;

import com.sankai.agent.mcp.server.McpToolRegistry;
import com.sankai.agent.orchestration.model.PlanStep;
import com.sankai.agent.orchestration.model.StepResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executor（执行者）角色 —— 编排流水线第二阶段。
 *
 * <p>按 Planner 生成的计划，<b>逐步调用 MCP 工具</b>并收集每一步的结果。
 * 执行过程中任何单步失败不会中断整体流程，而是记录错误继续执行后续步骤。
 *
 * <h3>核心职责：</h3>
 * <ol>
 *   <li>按步骤顺序调用工具</li>
 *   <li>记录每步的入参、出参、耗时、成功/失败状态</li>
 *   <li>对单步失败做容错处理（跳过继续）</li>
 *   <li>输出完整的执行记录供 Reviewer 复核</li>
 * </ol>
 *
 * <h3>工作流：</h3>
 * <pre>{@code
 * Plan: [Step1, Step2, Step3]
 *     │
 *     ▼
 * [Executor]
 *   ├── Step1 → query_tickets → result1 ✅ (120ms)
 *   ├── Step2 → query_tickets → result2 ✅ (95ms)
 *   └── Step3 → query_logs    → result3 ✅ (80ms)
 *     │
 *     ▼
 * Results: [result1, result2, result3]
 * }</pre>
 *
 * <p>设计原则：<b>Executor 不做任何推理</b>，它是纯粹的"执行者"，
 * 只负责忠实地执行计划中的每一步。智能决策交给 Planner 和 Reviewer。
 */
@Service
public class ExecutorService {

    private static final Logger log = LoggerFactory.getLogger(ExecutorService.class);

    private final McpToolRegistry toolRegistry;

    public ExecutorService(McpToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 执行全部计划步骤。
     *
     * <p>按步骤列表的顺序依次执行。每一步调用对应工具，
     * 记录结果后继续下一步。单步失败不影响后续步骤执行。
     *
     * @param steps Planner 生成的执行计划
     * @return 每一步的执行结果列表（与步骤一一对应）
     */
    public List<StepResult> executeAll(List<PlanStep> steps) {
        log.info("[Executor] 开始执行计划，共 {} 步", steps.size());

        List<StepResult> results = new ArrayList<>();
        for (PlanStep step : steps) {
            StepResult result = executeSingleStep(step);
            results.add(result);
        }

        long successCount = results.stream().filter(StepResult::isSuccess).count();
        log.info("[Executor] 执行完成: 总步骤={}, 成功={}, 失败={}",
                results.size(), successCount, results.size() - successCount);

        return results;
    }

    /**
     * 执行单个步骤。
     *
     * <p>调用 {@link McpToolRegistry} 中的工具，捕获异常并生成结果记录。
     *
     * @param step 要执行的步骤
     * @return 步骤执行结果
     */
    private StepResult executeSingleStep(PlanStep step) {
        log.info("[Executor] 执行步骤 {}: tool={}, purpose={}",
                step.getStepId(), step.getTool(), step.getPurpose());

        Map<String, Object> arguments = step.getArguments() != null
                ? step.getArguments()
                : Map.of();

        long startTime = System.currentTimeMillis();
        try {
            Object result = toolRegistry.invokeTool(step.getTool(), arguments);
            long duration = System.currentTimeMillis() - startTime;

            log.info("[Executor] 步骤 {} 执行成功，耗时 {}ms", step.getStepId(), duration);
            return StepResult.ok(step.getStepId(), step.getTool(), arguments, result, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("[Executor] 步骤 {} 执行失败: {}", step.getStepId(), e.getMessage());
            return StepResult.fail(step.getStepId(), step.getTool(), arguments,
                    e.getMessage(), duration);
        }
    }
}
