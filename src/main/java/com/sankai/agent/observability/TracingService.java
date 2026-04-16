package com.sankai.agent.observability;

import com.sankai.agent.observability.model.TraceRecord;
import com.sankai.agent.observability.model.TraceSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 链路追踪服务 —— Day 5 核心组件。
 *
 * <p>提供 Trace/Span 的生命周期管理：创建、记录、查询、统计。
 * 使用内存环形缓冲区存储最近 N 条 Trace，无需外部存储依赖。
 *
 * <h3>线程模型：</h3>
 * <p>通过 ThreadLocal 维护当前请求的 Trace 上下文，
 * 同一请求内的所有 Span 自动关联到同一个 traceId。
 */
@Service
public class TracingService {

    private static final Logger log = LoggerFactory.getLogger(TracingService.class);

    /** 内存中保留的最近 N 条 Trace */
    @Value("${observability.trace-buffer-size:200}")
    private int bufferSize = 200;

    /** Trace 存储（线程安全的双端队列） */
    private final Deque<TraceRecord> traceBuffer = new ConcurrentLinkedDeque<>();

    /** 当前线程的 Trace 上下文 */
    private static final ThreadLocal<TraceRecord> CURRENT_TRACE = new ThreadLocal<>();

    /** 当前线程的 Span 栈（支持嵌套 Span） */
    private static final ThreadLocal<Deque<TraceSpan>> SPAN_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    // ==================== Trace 生命周期 ====================

    /**
     * 开始一条新的 Trace。
     *
     * @param entryPoint 入口类型（agent / orchestrate）
     * @param input      用户输入
     * @return traceId
     */
    public String startTrace(String entryPoint, String input) {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        TraceRecord record = new TraceRecord(traceId, entryPoint, input);
        CURRENT_TRACE.set(record);
        SPAN_STACK.get().clear();
        log.debug("[Tracing] 开始 Trace: {}, entryPoint={}", traceId, entryPoint);
        return traceId;
    }

    /**
     * 结束当前 Trace，计算汇总并存入缓冲区。
     *
     * @param outputPreview 最终输出预览（截断前 200 字）
     */
    public void endTrace(String outputPreview) {
        TraceRecord record = CURRENT_TRACE.get();
        if (record == null) return;

        record.setOutputPreview(outputPreview != null && outputPreview.length() > 200
                ? outputPreview.substring(0, 200) + "..." : outputPreview);
        record.computeSummary();

        // 存入缓冲区
        traceBuffer.addFirst(record);
        while (traceBuffer.size() > bufferSize) {
            traceBuffer.removeLast();
        }

        log.info("[Tracing] 结束 Trace: {}, 耗时={}ms, token={}, cost=¥{}, llm调用={}, 工具调用={}, 成功={}",
                record.getTraceId(), record.getTotalDurationMs(),
                record.getTotalTokenEstimate(),
                String.format("%.4f", record.getTotalCostEstimate()),
                record.getLlmCallCount(), record.getToolCallCount(),
                record.isSuccess());

        CURRENT_TRACE.remove();
        SPAN_STACK.remove();
    }

    /**
     * 标记当前 Trace 失败。
     */
    public void failTrace(String errorMessage) {
        TraceRecord record = CURRENT_TRACE.get();
        if (record != null) {
            record.setErrorMessage(errorMessage);
        }
    }

    // ==================== Span 生命周期 ====================

    /**
     * 开始一个新的 Span。
     *
     * @param operationType 操作类型
     * @param operationName 操作名称
     * @return 新建的 Span
     */
    public TraceSpan startSpan(String operationType, String operationName) {
        TraceRecord trace = CURRENT_TRACE.get();
        if (trace == null) return null;

        Deque<TraceSpan> stack = SPAN_STACK.get();
        String parentId = stack.isEmpty() ? null : stack.peek().getSpanId();
        TraceSpan span = TraceSpan.start(trace.getTraceId(), parentId, operationType, operationName);

        // 如果是根 Span，设置到 TraceRecord
        if (stack.isEmpty()) {
            trace.setRootSpan(span);
        } else {
            // 添加到父 Span 的 children
            stack.peek().addChild(span);
        }

        stack.push(span);
        return span;
    }

    /**
     * 结束当前 Span。
     */
    public void endSpan(boolean success) {
        Deque<TraceSpan> stack = SPAN_STACK.get();
        if (stack.isEmpty()) return;

        TraceSpan span = stack.pop();
        span.end(success);
    }

    /**
     * 结束当前 Span（失败）。
     */
    public void endSpanWithError(String errorMessage) {
        Deque<TraceSpan> stack = SPAN_STACK.get();
        if (stack.isEmpty()) return;

        TraceSpan span = stack.pop();
        span.endWithError(errorMessage);
    }

    /**
     * 获取当前 Span 并设置 token/cost。
     */
    public void recordTokenCost(int tokenEstimate, double costEstimate) {
        Deque<TraceSpan> stack = SPAN_STACK.get();
        if (!stack.isEmpty()) {
            stack.peek().withTokenCost(tokenEstimate, costEstimate);
        }
    }

    /**
     * 给当前 Span 添加属性。
     */
    public void addSpanAttribute(String key, Object value) {
        Deque<TraceSpan> stack = SPAN_STACK.get();
        if (!stack.isEmpty()) {
            stack.peek().attr(key, value);
        }
    }

    // ==================== 查询接口 ====================

    /**
     * 获取最近的 N 条 Trace 记录。
     */
    public List<TraceRecord> getRecentTraces(int limit) {
        return traceBuffer.stream().limit(limit).toList();
    }

    /**
     * 按 traceId 查询。
     */
    public Optional<TraceRecord> getTrace(String traceId) {
        return traceBuffer.stream()
                .filter(t -> t.getTraceId().equals(traceId))
                .findFirst();
    }

    /**
     * 获取汇总统计指标。
     */
    public Map<String, Object> getMetrics() {
        List<TraceRecord> all = new ArrayList<>(traceBuffer);
        if (all.isEmpty()) {
            return Map.of("totalRequests", 0, "message", "暂无请求记录");
        }

        long totalRequests = all.size();
        long successCount = all.stream().filter(TraceRecord::isSuccess).count();
        double avgDuration = all.stream().mapToLong(TraceRecord::getTotalDurationMs).average().orElse(0);
        long p99Duration = all.stream().mapToLong(TraceRecord::getTotalDurationMs)
                .sorted().skip((long) (totalRequests * 0.99)).findFirst().orElse(0);
        int totalTokens = all.stream().mapToInt(TraceRecord::getTotalTokenEstimate).sum();
        double totalCost = all.stream().mapToDouble(TraceRecord::getTotalCostEstimate).sum();
        int totalLlmCalls = all.stream().mapToInt(TraceRecord::getLlmCallCount).sum();
        int totalToolCalls = all.stream().mapToInt(TraceRecord::getToolCallCount).sum();

        // 失败原因统计
        Map<String, Long> failReasons = new LinkedHashMap<>();
        all.stream()
                .filter(t -> !t.isSuccess() && t.getErrorMessage() != null)
                .forEach(t -> failReasons.merge(
                        t.getErrorMessage().length() > 80
                                ? t.getErrorMessage().substring(0, 80) : t.getErrorMessage(),
                        1L, Long::sum));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalRequests", totalRequests);
        metrics.put("successCount", successCount);
        metrics.put("failCount", totalRequests - successCount);
        metrics.put("successRate", String.format("%.1f%%", (double) successCount / totalRequests * 100));
        metrics.put("avgDurationMs", (long) avgDuration);
        metrics.put("p99DurationMs", p99Duration);
        metrics.put("totalTokenEstimate", totalTokens);
        metrics.put("totalCostEstimate", String.format("¥%.4f", totalCost));
        metrics.put("totalLlmCalls", totalLlmCalls);
        metrics.put("totalToolCalls", totalToolCalls);
        metrics.put("failReasons", failReasons);
        return metrics;
    }

    /** 获取缓冲区中的总 Trace 数 */
    public int getTraceCount() {
        return traceBuffer.size();
    }
}
