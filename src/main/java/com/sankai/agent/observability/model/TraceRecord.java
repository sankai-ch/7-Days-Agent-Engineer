package com.sankai.agent.observability.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单次请求的完整链路记录。
 *
 * <p>一个 TraceRecord 对应一次完整的用户请求（Agent chat 或 Orchestrate），
 * 内含一棵 Span 树 + 汇总统计。
 */
public class TraceRecord {

    /** Trace ID */
    private String traceId;

    /** 请求入口类型：agent / orchestrate */
    private String entryPoint;

    /** 用户原始输入 */
    private String input;

    /** 最终回答（截断前 200 字） */
    private String outputPreview;

    /** 根 Span（包含完整 Span 树） */
    private TraceSpan rootSpan;

    /** 请求时间 */
    private Instant timestamp;

    /** 是否成功 */
    private boolean success;

    /** 总耗时 (ms) */
    private long totalDurationMs;

    /** 总 Token 估算 */
    private int totalTokenEstimate;

    /** 总成本估算 (元) */
    private double totalCostEstimate;

    /** LLM 调用次数 */
    private int llmCallCount;

    /** 工具调用次数 */
    private int toolCallCount;

    /** 错误信息 */
    private String errorMessage;

    public TraceRecord() {
        this.timestamp = Instant.now();
    }

    public TraceRecord(String traceId, String entryPoint, String input) {
        this();
        this.traceId = traceId;
        this.entryPoint = entryPoint;
        this.input = input;
    }

    /**
     * 从根 Span 递归汇总统计数据。
     */
    public void computeSummary() {
        if (rootSpan == null) return;
        Map<String, int[]> summary = new LinkedHashMap<>(); // [tokenSum, costSum*10000, count]
        aggregateSpan(rootSpan, summary);

        this.totalDurationMs = rootSpan.getDurationMs();
        this.success = rootSpan.isSuccess();
        this.totalTokenEstimate = summary.values().stream().mapToInt(a -> a[0]).sum();
        this.totalCostEstimate = summary.values().stream().mapToInt(a -> a[1]).sum() / 10000.0;
        this.llmCallCount = countByType(rootSpan, "llm") + countByType(rootSpan, "planner")
                + countByType(rootSpan, "reviewer") + countByType(rootSpan, "summarizer");
        this.toolCallCount = countByType(rootSpan, "tool");
    }

    private void aggregateSpan(TraceSpan span, Map<String, int[]> summary) {
        summary.merge(span.getOperationType(),
                new int[]{span.getTokenEstimate(), (int) (span.getCostEstimate() * 10000), 1},
                (a, b) -> new int[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]});
        for (TraceSpan child : span.getChildren()) {
            aggregateSpan(child, summary);
        }
    }

    private int countByType(TraceSpan span, String type) {
        int count = type.equals(span.getOperationType()) ? 1 : 0;
        for (TraceSpan child : span.getChildren()) {
            count += countByType(child, type);
        }
        return count;
    }

    // ==================== Getter / Setter ====================

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getEntryPoint() { return entryPoint; }
    public void setEntryPoint(String entryPoint) { this.entryPoint = entryPoint; }
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
    public String getOutputPreview() { return outputPreview; }
    public void setOutputPreview(String outputPreview) { this.outputPreview = outputPreview; }
    public TraceSpan getRootSpan() { return rootSpan; }
    public void setRootSpan(TraceSpan rootSpan) { this.rootSpan = rootSpan; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }
    public int getTotalTokenEstimate() { return totalTokenEstimate; }
    public void setTotalTokenEstimate(int totalTokenEstimate) { this.totalTokenEstimate = totalTokenEstimate; }
    public double getTotalCostEstimate() { return totalCostEstimate; }
    public void setTotalCostEstimate(double totalCostEstimate) { this.totalCostEstimate = totalCostEstimate; }
    public int getLlmCallCount() { return llmCallCount; }
    public void setLlmCallCount(int llmCallCount) { this.llmCallCount = llmCallCount; }
    public int getToolCallCount() { return toolCallCount; }
    public void setToolCallCount(int toolCallCount) { this.toolCallCount = toolCallCount; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
