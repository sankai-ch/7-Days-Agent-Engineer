package com.sankai.agent.orchestration.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 编排流水线的完整响应。
 *
 * <p>包含整条流水线的全部中间产物，实现完全的可观测性：
 * <ul>
 *   <li>Planner 生成的执行计划</li>
 *   <li>Executor 的逐步执行结果</li>
 *   <li>Reviewer 的复核裁决</li>
 *   <li>最终汇总回答</li>
 *   <li>全链路耗时</li>
 * </ul>
 *
 * <h3>响应示例：</h3>
 * <pre>{@code
 * POST /v1/orchestrate
 * {
 *   "answer": "综合分析结果如下...",
 *   "plan": [ {stepId:1, tool:"query_tickets"...}, {stepId:2, tool:"query_logs"...} ],
 *   "stepResults": [ {stepId:1, success:true...}, {stepId:2, success:true...} ],
 *   "review": { "verdict":"PASS", "qualityScore":85 },
 *   "totalDurationMs": 3200,
 *   "success": true
 * }
 * }</pre>
 */
public class OrchestrationResponse {

    /** 最终汇总回答 */
    private String answer;

    /** Planner 生成的执行计划 */
    private List<PlanStep> plan = new ArrayList<>();

    /** Executor 的逐步执行结果 */
    private List<StepResult> stepResults = new ArrayList<>();

    /** Reviewer 的复核裁决 */
    private ReviewVerdict review;

    /** 补充执行的结果（仅当 Reviewer 要求补充时） */
    private List<StepResult> supplementResults = new ArrayList<>();

    /** 总耗时（毫秒） */
    private long totalDurationMs;

    /** 是否成功 */
    private boolean success;

    /** 错误信息 */
    private String errorMessage;

    /** 编排模式标识 */
    private String mode = "planner-executor-reviewer";

    // ==================== 静态工厂方法 ====================

    public static OrchestrationResponse success(String answer, List<PlanStep> plan,
                                                 List<StepResult> stepResults,
                                                 ReviewVerdict review, long totalDurationMs) {
        OrchestrationResponse r = new OrchestrationResponse();
        r.answer = answer;
        r.plan = plan;
        r.stepResults = stepResults;
        r.review = review;
        r.totalDurationMs = totalDurationMs;
        r.success = true;
        return r;
    }

    public static OrchestrationResponse failure(String errorMessage, long totalDurationMs) {
        OrchestrationResponse r = new OrchestrationResponse();
        r.success = false;
        r.errorMessage = errorMessage;
        r.answer = "编排执行失败: " + errorMessage;
        r.totalDurationMs = totalDurationMs;
        return r;
    }

    // ==================== Getter / Setter ====================

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public List<PlanStep> getPlan() { return plan; }
    public void setPlan(List<PlanStep> plan) { this.plan = plan; }
    public List<StepResult> getStepResults() { return stepResults; }
    public void setStepResults(List<StepResult> stepResults) { this.stepResults = stepResults; }
    public ReviewVerdict getReview() { return review; }
    public void setReview(ReviewVerdict review) { this.review = review; }
    public List<StepResult> getSupplementResults() { return supplementResults; }
    public void setSupplementResults(List<StepResult> supplementResults) { this.supplementResults = supplementResults; }
    public long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
