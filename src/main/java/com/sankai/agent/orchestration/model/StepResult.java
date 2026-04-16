package com.sankai.agent.orchestration.model;

import java.util.Map;

/**
 * 单步执行记录。
 *
 * <p>由 Executor 在完成每个 {@link PlanStep} 后生成，
 * 记录工具调用的输入、输出、耗时和成功状态。
 */
public class StepResult {

    /** 对应的步骤 ID */
    private int stepId;

    /** 调用的工具名称 */
    private String tool;

    /** 传入的参数 */
    private Map<String, Object> arguments;

    /** 工具返回的原始结果 */
    private Object result;

    /** 执行耗时（毫秒） */
    private long durationMs;

    /** 是否成功 */
    private boolean success;

    /** 失败时的错误信息 */
    private String errorMessage;

    public StepResult() {}

    /**
     * 构建成功的步骤结果。
     */
    public static StepResult ok(int stepId, String tool, Map<String, Object> arguments,
                                Object result, long durationMs) {
        StepResult r = new StepResult();
        r.stepId = stepId;
        r.tool = tool;
        r.arguments = arguments;
        r.result = result;
        r.durationMs = durationMs;
        r.success = true;
        return r;
    }

    /**
     * 构建失败的步骤结果。
     */
    public static StepResult fail(int stepId, String tool, Map<String, Object> arguments,
                                  String errorMessage, long durationMs) {
        StepResult r = new StepResult();
        r.stepId = stepId;
        r.tool = tool;
        r.arguments = arguments;
        r.errorMessage = errorMessage;
        r.durationMs = durationMs;
        r.success = false;
        return r;
    }

    // ==================== Getter / Setter ====================

    public int getStepId() { return stepId; }
    public void setStepId(int stepId) { this.stepId = stepId; }
    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }
    public Map<String, Object> getArguments() { return arguments; }
    public void setArguments(Map<String, Object> arguments) { this.arguments = arguments; }
    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
