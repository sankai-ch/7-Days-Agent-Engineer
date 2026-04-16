package com.sankai.agent;

import com.sankai.agent.observability.CostTracker;
import com.sankai.agent.observability.TracingService;
import com.sankai.agent.observability.model.TraceRecord;
import com.sankai.agent.observability.model.TraceSpan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Day 5 可观测与评测单元测试。
 */
class ObservabilityTest {

    // ==================== TraceSpan 测试 ====================

    @Test
    @DisplayName("TraceSpan - start/end 应正确计算耗时")
    void testTraceSpanLifecycle() throws InterruptedException {
        TraceSpan span = TraceSpan.start("trace-1", null, "tool", "query_logs");
        assertNotNull(span.getSpanId());
        assertEquals("trace-1", span.getTraceId());
        assertNotNull(span.getStartTime());

        Thread.sleep(10);
        span.end(true);
        assertTrue(span.isSuccess());
        assertTrue(span.getDurationMs() >= 10);
        assertNotNull(span.getEndTime());
    }

    @Test
    @DisplayName("TraceSpan - endWithError 应记录错误信息")
    void testTraceSpanError() {
        TraceSpan span = TraceSpan.start("trace-2", null, "llm", "chat");
        span.endWithError("timeout");
        assertFalse(span.isSuccess());
        assertEquals("timeout", span.getErrorMessage());
    }

    @Test
    @DisplayName("TraceSpan - attr/withTokenCost 链式调用")
    void testTraceSpanAttributes() {
        TraceSpan span = TraceSpan.start("trace-3", null, "planner", "plan");
        span.attr("steps", 3).withTokenCost(500, 0.001);
        assertEquals(3, span.getAttributes().get("steps"));
        assertEquals(500, span.getTokenEstimate());
        assertEquals(0.001, span.getCostEstimate(), 0.0001);
    }

    @Test
    @DisplayName("TraceSpan - 子 Span 应正确关联")
    void testTraceSpanChildren() {
        TraceSpan parent = TraceSpan.start("trace-4", null, "orchestration", "root");
        TraceSpan child1 = TraceSpan.start("trace-4", parent.getSpanId(), "planner", "plan");
        TraceSpan child2 = TraceSpan.start("trace-4", parent.getSpanId(), "executor", "exec");
        parent.addChild(child1);
        parent.addChild(child2);

        assertEquals(2, parent.getChildren().size());
        assertEquals(parent.getSpanId(), child1.getParentSpanId());
    }

    // ==================== TracingService 测试 ====================

    @Test
    @DisplayName("TracingService - startTrace/endTrace 应正确存储记录")
    void testTracingServiceLifecycle() {
        TracingService service = new TracingService();

        String traceId = service.startTrace("agent", "test input");
        assertNotNull(traceId);

        // 创建一个 root span
        TraceSpan rootSpan = service.startSpan("agent", "chat");
        assertNotNull(rootSpan);

        service.recordTokenCost(100, 0.0005);
        service.addSpanAttribute("toolCount", 1);

        service.endSpan(true);
        service.endTrace("test output");

        // 验证存储
        assertEquals(1, service.getTraceCount());
        var trace = service.getTrace(traceId);
        assertTrue(trace.isPresent());
        assertEquals("test input", trace.get().getInput());
        assertEquals("test output", trace.get().getOutputPreview());
    }

    @Test
    @DisplayName("TracingService - getMetrics 应正确汇总统计")
    void testTracingServiceMetrics() {
        TracingService service = new TracingService();

        // 模拟两条 trace
        for (int i = 0; i < 2; i++) {
            service.startTrace("agent", "input-" + i);
            TraceSpan span = service.startSpan("agent", "chat");
            span.withTokenCost(100, 0.001);
            service.endSpan(true);
            service.endTrace("output-" + i);
        }

        Map<String, Object> metrics = service.getMetrics();
        assertEquals(2L, metrics.get("totalRequests"));
        assertEquals(2L, metrics.get("successCount"));
        assertEquals(0L, metrics.get("failCount"));
        assertEquals("100.0%", metrics.get("successRate"));
    }

    @Test
    @DisplayName("TracingService - failTrace 应记录错误")
    void testTracingServiceFail() {
        TracingService service = new TracingService();

        service.startTrace("agent", "bad input");
        service.startSpan("agent", "chat");
        service.endSpanWithError("LLM timeout");
        service.failTrace("LLM timeout");
        service.endTrace("ERROR");

        var trace = service.getRecentTraces(1).get(0);
        assertEquals("LLM timeout", trace.getErrorMessage());
        assertFalse(trace.isSuccess());
    }

    // ==================== TraceRecord 测试 ====================

    @Test
    @DisplayName("TraceRecord - computeSummary 应正确汇总 Span 树")
    void testTraceRecordSummary() {
        TraceRecord record = new TraceRecord("t1", "orchestrate", "test");

        TraceSpan root = TraceSpan.start("t1", null, "orchestration", "root");
        TraceSpan planner = TraceSpan.start("t1", root.getSpanId(), "planner", "plan");
        planner.withTokenCost(300, 0.001).end(true);
        TraceSpan tool = TraceSpan.start("t1", root.getSpanId(), "tool", "query_logs");
        tool.end(true);
        root.addChild(planner);
        root.addChild(tool);
        root.end(true);

        record.setRootSpan(root);
        record.computeSummary();

        assertTrue(record.isSuccess());
        assertEquals(300, record.getTotalTokenEstimate());
        assertEquals(1, record.getToolCallCount());
    }

    // ==================== CostTracker 测试 ====================

    @Test
    @DisplayName("CostTracker - 中文 Token 估算")
    void testCostTrackerChinese() {
        CostTracker tracker = new CostTracker();
        int tokens = tracker.estimateTokens("你好世界测试");
        // 6 个中文字 * 0.7 ≈ 4.2 + 1 = 5
        assertTrue(tokens > 0 && tokens < 10);
    }

    @Test
    @DisplayName("CostTracker - 英文 Token 估算")
    void testCostTrackerEnglish() {
        CostTracker tracker = new CostTracker();
        int tokens = tracker.estimateTokens("Hello World Test");
        // 16 字符 / 4 ≈ 4 + 1 = 5
        assertTrue(tokens > 0 && tokens < 10);
    }

    @Test
    @DisplayName("CostTracker - 空文本应返回 0")
    void testCostTrackerEmpty() {
        CostTracker tracker = new CostTracker();
        assertEquals(0, tracker.estimateTokens(null));
        assertEquals(0, tracker.estimateTokens(""));
    }

    @Test
    @DisplayName("CostTracker - estimate 应返回 [token, cost]")
    void testCostTrackerEstimate() {
        CostTracker tracker = new CostTracker();
        double[] est = tracker.estimate("输入文本", "输出结果文本");
        assertTrue(est[0] > 0); // token > 0
        assertTrue(est[1] >= 0); // cost >= 0
    }
}
