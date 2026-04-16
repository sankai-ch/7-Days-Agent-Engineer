package com.sankai.agent.observability;

import com.sankai.agent.observability.model.TraceRecord;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可观测性 REST API —— 暴露 Trace 查询和 Metrics 统计接口。
 *
 * <h3>接口列表：</h3>
 * <table>
 *   <tr><th>方法</th><th>路径</th><th>说明</th></tr>
 *   <tr><td>GET</td><td>/v1/traces</td><td>查询最近的链路追踪记录</td></tr>
 *   <tr><td>GET</td><td>/v1/traces/{traceId}</td><td>查询单条 Trace 详情（含 Span 树）</td></tr>
 *   <tr><td>GET</td><td>/v1/metrics</td><td>查询汇总统计指标</td></tr>
 * </table>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * # 查看汇总指标
 * curl http://localhost:8080/v1/metrics
 *
 * # 查看最近 5 条 Trace
 * curl http://localhost:8080/v1/traces?limit=5
 *
 * # 查看某条 Trace 的详细 Span 树
 * curl http://localhost:8080/v1/traces/abc123
 * }</pre>
 */
@RestController
@RequestMapping("/v1")
public class ObservabilityController {

    private final TracingService tracingService;

    public ObservabilityController(TracingService tracingService) {
        this.tracingService = tracingService;
    }

    /**
     * 查询汇总统计指标。
     *
     * <p>返回成功率、时延、成本、Token 用量、失败原因分布等。
     */
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return tracingService.getMetrics();
    }

    /**
     * 查询最近的 Trace 列表（概要信息，不含 Span 树）。
     *
     * @param limit 返回数量上限，默认 20
     */
    @GetMapping("/traces")
    public Map<String, Object> listTraces(@RequestParam(defaultValue = "20") int limit) {
        List<TraceRecord> traces = tracingService.getRecentTraces(limit);

        // 返回概要列表（去掉 rootSpan 树减少数据量）
        List<Map<String, Object>> summaries = traces.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("traceId", t.getTraceId());
            m.put("entryPoint", t.getEntryPoint());
            m.put("input", t.getInput());
            m.put("outputPreview", t.getOutputPreview());
            m.put("success", t.isSuccess());
            m.put("totalDurationMs", t.getTotalDurationMs());
            m.put("totalTokenEstimate", t.getTotalTokenEstimate());
            m.put("totalCostEstimate", String.format("¥%.4f", t.getTotalCostEstimate()));
            m.put("llmCallCount", t.getLlmCallCount());
            m.put("toolCallCount", t.getToolCallCount());
            m.put("timestamp", t.getTimestamp());
            if (t.getErrorMessage() != null) m.put("error", t.getErrorMessage());
            return m;
        }).toList();

        return Map.of(
                "total", summaries.size(),
                "traces", summaries
        );
    }

    /**
     * 查询单条 Trace 的详细信息（含完整 Span 树）。
     *
     * @param traceId Trace ID
     */
    @GetMapping("/traces/{traceId}")
    public Object getTrace(@PathVariable String traceId) {
        return tracingService.getTrace(traceId)
                .map(t -> (Object) t)
                .orElse(Map.of("error", "Trace not found: " + traceId));
    }
}
