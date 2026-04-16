package com.sankai.agent.observability.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 链路追踪 Span —— 单次操作的观测记录。
 *
 * <p>每个 Span 代表一次可观测的操作（LLM 调用、工具执行、阶段耗时等），
 * 多个 Span 通过 traceId 关联成一条完整的调用链。
 *
 * <h3>Span 层级示例（一次编排请求）：</h3>
 * <pre>{@code
 * traceId: abc-123
 * ├── [orchestration] 总编排         3200ms
 * │   ├── [planner]   规划阶段        800ms  (tokenEstimate: 1200)
 * │   ├── [executor]  执行阶段        1400ms
 * │   │   ├── [tool]  query_logs       85ms
 * │   │   └── [tool]  query_tickets   120ms
 * │   ├── [reviewer]  复核阶段        600ms  (tokenEstimate: 900)
 * │   └── [summarizer] 汇总阶段       400ms  (tokenEstimate: 800)
 * }</pre>
 */
public class TraceSpan {

    /** Span 唯一 ID */
    private String spanId;

    /** 所属 Trace ID（同一请求的所有 Span 共享） */
    private String traceId;

    /** 父 Span ID（根 Span 为 null） */
    private String parentSpanId;

    /**
     * 操作类型：
     * <ul>
     *   <li>{@code orchestration} — 编排总流程</li>
     *   <li>{@code agent} — 单 Agent 流程</li>
     *   <li>{@code planner} — Planner 阶段</li>
     *   <li>{@code executor} — Executor 阶段</li>
     *   <li>{@code reviewer} — Reviewer 阶段</li>
     *   <li>{@code summarizer} — 汇总阶段</li>
     *   <li>{@code tool} — 工具调用</li>
     *   <li>{@code llm} — LLM 调用</li>
     * </ul>
     */
    private String operationType;

    /** 操作名称（如工具名、阶段名） */
    private String operationName;

    /** 开始时间 */
    private Instant startTime;

    /** 结束时间 */
    private Instant endTime;

    /** 耗时（毫秒） */
    private long durationMs;

    /** 是否成功 */
    private boolean success;

    /** 错误信息（失败时） */
    private String errorMessage;

    /** 估算的 Token 用量（仅 LLM 相关 Span） */
    private int tokenEstimate;

    /** 估算成本（元，仅 LLM 相关 Span） */
    private double costEstimate;

    /** 自定义属性（扩展字段） */
    private Map<String, Object> attributes = new LinkedHashMap<>();

    /** 子 Span 列表 */
    private List<TraceSpan> children = new ArrayList<>();

    // ==================== 构造与工厂方法 ====================

    public TraceSpan() {
        this.spanId = UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 创建一个新的 Span 并自动开始计时。
     *
     * @param traceId       Trace ID
     * @param parentSpanId  父 Span ID
     * @param operationType 操作类型
     * @param operationName 操作名称
     * @return 新建的 Span（已设置 startTime）
     */
    public static TraceSpan start(String traceId, String parentSpanId,
                                   String operationType, String operationName) {
        TraceSpan span = new TraceSpan();
        span.traceId = traceId;
        span.parentSpanId = parentSpanId;
        span.operationType = operationType;
        span.operationName = operationName;
        span.startTime = Instant.now();
        return span;
    }

    /**
     * 结束此 Span，计算耗时。
     */
    public TraceSpan end(boolean success) {
        this.endTime = Instant.now();
        this.durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();
        this.success = success;
        return this;
    }

    /**
     * 结束此 Span（失败），附带错误信息。
     */
    public TraceSpan endWithError(String errorMessage) {
        this.endTime = Instant.now();
        this.durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();
        this.success = false;
        this.errorMessage = errorMessage;
        return this;
    }

    /** 添加自定义属性 */
    public TraceSpan attr(String key, Object value) {
        this.attributes.put(key, value);
        return this;
    }

    /** 设置 token 和成本 */
    public TraceSpan withTokenCost(int tokenEstimate, double costEstimate) {
        this.tokenEstimate = tokenEstimate;
        this.costEstimate = costEstimate;
        return this;
    }

    /** 添加子 Span */
    public void addChild(TraceSpan child) {
        this.children.add(child);
    }

    // ==================== Getter / Setter ====================

    public String getSpanId() { return spanId; }
    public void setSpanId(String spanId) { this.spanId = spanId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getParentSpanId() { return parentSpanId; }
    public void setParentSpanId(String parentSpanId) { this.parentSpanId = parentSpanId; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public int getTokenEstimate() { return tokenEstimate; }
    public void setTokenEstimate(int tokenEstimate) { this.tokenEstimate = tokenEstimate; }
    public double getCostEstimate() { return costEstimate; }
    public void setCostEstimate(double costEstimate) { this.costEstimate = costEstimate; }
    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
    public List<TraceSpan> getChildren() { return children; }
    public void setChildren(List<TraceSpan> children) { this.children = children; }
}
