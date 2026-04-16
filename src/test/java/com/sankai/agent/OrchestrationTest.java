package com.sankai.agent;

import com.sankai.agent.mcp.protocol.McpToolInfo;
import com.sankai.agent.mcp.server.McpToolRegistry;
import com.sankai.agent.mcp.tool.McpTool;
import com.sankai.agent.orchestration.model.*;
import com.sankai.agent.orchestration.role.ExecutorService;
import com.sankai.agent.orchestration.role.ReviewerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Day 4 编排流水线单元测试。
 *
 * <p>不依赖 LLM 调用，测试 Executor 和 Reviewer 的回退逻辑，
 * 以及各模型类的构建方法。
 */
class OrchestrationTest {

    // ==================== 模型类测试 ====================

    @Test
    @DisplayName("PlanStep - 应正确构建计划步骤")
    void testPlanStep() {
        PlanStep step = new PlanStep(1, "query_logs",
                Map.of("level", "ERROR"), "查找错误日志");

        assertEquals(1, step.getStepId());
        assertEquals("query_logs", step.getTool());
        assertEquals("ERROR", step.getArguments().get("level"));
        assertEquals("查找错误日志", step.getPurpose());
        assertTrue(step.getDependsOn().isEmpty());
    }

    @Test
    @DisplayName("StepResult - ok 和 fail 静态工厂应正确构建")
    void testStepResult() {
        StepResult ok = StepResult.ok(1, "query_logs",
                Map.of("level", "ERROR"), Map.of("total", 3), 120);
        assertTrue(ok.isSuccess());
        assertEquals(1, ok.getStepId());
        assertEquals(120, ok.getDurationMs());
        assertNull(ok.getErrorMessage());

        StepResult fail = StepResult.fail(2, "query_tickets",
                Map.of(), "连接超时", 500);
        assertFalse(fail.isSuccess());
        assertEquals("连接超时", fail.getErrorMessage());
    }

    @Test
    @DisplayName("ReviewVerdict - PASS / NEEDS_SUPPLEMENT / FAIL 构建正确")
    void testReviewVerdict() {
        ReviewVerdict pass = ReviewVerdict.pass("数据充分", 85);
        assertTrue(pass.isPassed());
        assertFalse(pass.needsSupplement());
        assertEquals(85, pass.getQualityScore());

        ReviewVerdict supp = ReviewVerdict.needsSupplement("需要补充日志", 55,
                List.of(new PlanStep(3, "query_logs", Map.of(), "补充日志")));
        assertFalse(supp.isPassed());
        assertTrue(supp.needsSupplement());
        assertEquals(1, supp.getSupplementSteps().size());

        ReviewVerdict fail = ReviewVerdict.fail("全部失败");
        assertFalse(fail.isPassed());
        assertFalse(fail.needsSupplement());
        assertEquals(0, fail.getQualityScore());
    }

    @Test
    @DisplayName("OrchestrationResponse - success/failure 构建正确")
    void testOrchestrationResponse() {
        OrchestrationResponse success = OrchestrationResponse.success(
                "分析完成", List.of(), List.of(),
                ReviewVerdict.pass("ok", 90), 3000);
        assertTrue(success.isSuccess());
        assertEquals("分析完成", success.getAnswer());
        assertEquals(3000, success.getTotalDurationMs());
        assertEquals("planner-executor-reviewer", success.getMode());

        OrchestrationResponse failure = OrchestrationResponse.failure("网络错误", 100);
        assertFalse(failure.isSuccess());
        assertEquals("网络错误", failure.getErrorMessage());
    }

    // ==================== Executor 测试 ====================

    @Test
    @DisplayName("Executor - 正常工具应返回成功结果")
    void testExecutorSuccess() {
        // 准备一个 mock 工具注册中心
        McpTool echoTool = new McpTool() {
            @Override public String getName() { return "echo"; }
            @Override public McpToolInfo getToolInfo() {
                return new McpToolInfo("echo", "test", Map.of());
            }
            @Override public Object execute(Map<String, Object> args) {
                return Map.of("echo", args.getOrDefault("msg", ""));
            }
        };
        McpToolRegistry registry = new McpToolRegistry(List.of(echoTool));
        registry.init();

        ExecutorService executor = new ExecutorService(registry);

        List<PlanStep> plan = List.of(
                new PlanStep(1, "echo", Map.of("msg", "hello"), "测试"),
                new PlanStep(2, "echo", Map.of("msg", "world"), "测试2")
        );

        List<StepResult> results = executor.executeAll(plan);

        assertEquals(2, results.size());
        assertTrue(results.get(0).isSuccess());
        assertTrue(results.get(1).isSuccess());
    }

    @Test
    @DisplayName("Executor - 不存在的工具应返回失败结果但不中断")
    void testExecutorPartialFailure() {
        McpTool echoTool = new McpTool() {
            @Override public String getName() { return "echo"; }
            @Override public McpToolInfo getToolInfo() {
                return new McpToolInfo("echo", "test", Map.of());
            }
            @Override public Object execute(Map<String, Object> args) {
                return Map.of("ok", true);
            }
        };
        McpToolRegistry registry = new McpToolRegistry(List.of(echoTool));
        registry.init();

        ExecutorService executor = new ExecutorService(registry);

        List<PlanStep> plan = List.of(
                new PlanStep(1, "echo", Map.of(), "正常步骤"),
                new PlanStep(2, "nonexistent", Map.of(), "不存在的工具"),
                new PlanStep(3, "echo", Map.of(), "恢复步骤")
        );

        List<StepResult> results = executor.executeAll(plan);

        // 3 步都有结果（不中断）
        assertEquals(3, results.size());
        assertTrue(results.get(0).isSuccess());    // 步骤1成功
        assertFalse(results.get(1).isSuccess());   // 步骤2失败
        assertTrue(results.get(2).isSuccess());    // 步骤3继续成功
    }

    // ==================== Reviewer 回退逻辑测试 ====================

    @Test
    @DisplayName("Reviewer - 全部失败时应直接判 FAIL")
    void testReviewerAllFail() {
        ReviewerService reviewerService = new ReviewerService(null, null);

        List<StepResult> results = List.of(
                StepResult.fail(1, "a", Map.of(), "err1", 100),
                StepResult.fail(2, "b", Map.of(), "err2", 100)
        );

        ReviewVerdict verdict = reviewerService.review("test task", List.of(), results);
        assertEquals(ReviewVerdict.Verdict.FAIL, verdict.getVerdict());
    }
}
