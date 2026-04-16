package com.sankai.agent.observability;

import com.sankai.agent.agent.model.AgentResponse;
import com.sankai.agent.orchestration.model.OrchestrationResponse;
import com.sankai.agent.orchestration.model.StepResult;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 可观测性 AOP 切面 —— 自动织入链路追踪。
 *
 * <p>无侵入地拦截 Agent、Orchestration、各阶段和工具调用，
 * 自动创建 Span 记录耗时和结构化属性。
 *
 * <p><b>注意</b>：LLM 调用的 Token/Cost 不在此切面记录，
 * 而是由 {@link ObservableChatModel} 在每次 {@code chatModel.chat()} 时
 * 自动创建 {@code llm} 子 Span 精确记录。本切面只负责"阶段级"的 Span。
 *
 * <h3>Span 层级结构：</h3>
 * <pre>{@code
 * [orchestration] OrchestrationService.orchestrate    ← 本切面
 * ├── [planner]   PlannerService.plan                 ← 本切面
 * │   └── [llm]   chatModel.chat#1                    ← ObservableChatModel
 * ├── [executor]  ExecutorService.executeAll           ← 本切面
 * │   ├── [tool]  query_logs                          ← McpToolRegistry 已有日志
 * │   └── [tool]  query_tickets                       ← McpToolRegistry 已有日志
 * ├── [reviewer]  ReviewerService.review               ← 本切面
 * │   └── [llm]   chatModel.chat#2                    ← ObservableChatModel
 * └── [llm]       chatModel.chat#3 (summarize)        ← ObservableChatModel
 * }</pre>
 */
@Aspect
@Component
public class TracingAspect {

    private static final Logger log = LoggerFactory.getLogger(TracingAspect.class);

    private final TracingService tracingService;

    public TracingAspect(TracingService tracingService) {
        this.tracingService = tracingService;
    }

    // ==================== 单 Agent 入口 ====================

    /**
     * 拦截 AgentService.chat() —— 单 Agent 模式的根 Span + Trace 生命周期。
     */
    @Around("execution(* com.sankai.agent.agent.AgentService.chat(..))")
    public Object traceAgentChat(ProceedingJoinPoint pjp) throws Throwable {
        String input = pjp.getArgs().length > 0 ? (String) pjp.getArgs()[0] : "";
        tracingService.startTrace("agent", input);
        tracingService.startSpan("agent", "AgentService.chat");

        try {
            Object result = pjp.proceed();

            if (result instanceof AgentResponse resp) {
                tracingService.addSpanAttribute("toolCallCount",
                        resp.getToolCalls() != null ? resp.getToolCalls().size() : 0);
                tracingService.addSpanAttribute("success", resp.isSuccess());
            }

            tracingService.endSpan(true);
            String output = result instanceof AgentResponse r ? r.getAnswer() : "";
            tracingService.endTrace(output);
            return result;

        } catch (Throwable e) {
            tracingService.endSpanWithError(e.getMessage());
            tracingService.failTrace(e.getMessage());
            tracingService.endTrace("ERROR: " + e.getMessage());
            throw e;
        }
    }

    // ==================== 编排入口 ====================

    /**
     * 拦截 OrchestrationService.orchestrate() —— 编排模式的根 Span + Trace 生命周期。
     */
    @Around("execution(* com.sankai.agent.orchestration.OrchestrationService.orchestrate(..))")
    public Object traceOrchestrate(ProceedingJoinPoint pjp) throws Throwable {
        String input = pjp.getArgs().length > 0 ? (String) pjp.getArgs()[0] : "";
        tracingService.startTrace("orchestrate", input);
        tracingService.startSpan("orchestration", "OrchestrationService.orchestrate");

        try {
            Object result = pjp.proceed();

            if (result instanceof OrchestrationResponse resp) {
                tracingService.addSpanAttribute("planSteps",
                        resp.getPlan() != null ? resp.getPlan().size() : 0);
                tracingService.addSpanAttribute("qualityScore",
                        resp.getReview() != null ? resp.getReview().getQualityScore() : -1);
                tracingService.addSpanAttribute("toolCallCount",
                        resp.getStepResults() != null ? resp.getStepResults().size() : 0);
            }

            tracingService.endSpan(true);
            String output = result instanceof OrchestrationResponse r ? r.getAnswer() : "";
            tracingService.endTrace(output);
            return result;

        } catch (Throwable e) {
            tracingService.endSpanWithError(e.getMessage());
            tracingService.failTrace(e.getMessage());
            tracingService.endTrace("ERROR: " + e.getMessage());
            throw e;
        }
    }

    // ==================== 各阶段 Span ====================

    /**
     * 拦截 Planner 规划阶段。
     * <p>Token/Cost 由内部 chatModel.chat() 的 ObservableChatModel 自动记录。
     */
    @Around("execution(* com.sankai.agent.orchestration.role.PlannerService.plan(..))")
    public Object tracePlanner(ProceedingJoinPoint pjp) throws Throwable {
        tracingService.startSpan("planner", "PlannerService.plan");
        try {
            Object result = pjp.proceed();
            if (result instanceof java.util.List<?> list) {
                tracingService.addSpanAttribute("planSteps", list.size());
            }
            tracingService.endSpan(true);
            return result;
        } catch (Throwable e) {
            tracingService.endSpanWithError(e.getMessage());
            throw e;
        }
    }

    /**
     * 拦截 Executor 执行阶段。
     */
    @Around("execution(* com.sankai.agent.orchestration.role.ExecutorService.executeAll(..))")
    public Object traceExecutor(ProceedingJoinPoint pjp) throws Throwable {
        tracingService.startSpan("executor", "ExecutorService.executeAll");
        try {
            Object result = pjp.proceed();
            if (result instanceof java.util.List<?> list) {
                long successCount = list.stream()
                        .filter(r -> r instanceof StepResult sr && sr.isSuccess()).count();
                tracingService.addSpanAttribute("totalSteps", list.size());
                tracingService.addSpanAttribute("successSteps", successCount);
            }
            tracingService.endSpan(true);
            return result;
        } catch (Throwable e) {
            tracingService.endSpanWithError(e.getMessage());
            throw e;
        }
    }

    /**
     * 拦截 Reviewer 复核阶段。
     * <p>Token/Cost 由内部 chatModel.chat() 的 ObservableChatModel 自动记录。
     */
    @Around("execution(* com.sankai.agent.orchestration.role.ReviewerService.review(..))")
    public Object traceReviewer(ProceedingJoinPoint pjp) throws Throwable {
        tracingService.startSpan("reviewer", "ReviewerService.review");
        try {
            Object result = pjp.proceed();
            tracingService.endSpan(true);
            return result;
        } catch (Throwable e) {
            tracingService.endSpanWithError(e.getMessage());
            throw e;
        }
    }
}
