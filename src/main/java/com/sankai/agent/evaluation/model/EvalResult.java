package com.sankai.agent.evaluation.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条评测结果。
 */
public class EvalResult {

    /** 对应的用例 ID */
    private String caseId;

    /** 是否通过 */
    private boolean passed;

    /** 用户输入 */
    private String input;

    /** 实际输出回答 */
    private String actualOutput;

    /** 实际调用的工具列表 */
    private List<String> actualToolCalls = new ArrayList<>();

    /** 检查详情 */
    private List<String> checkDetails = new ArrayList<>();

    /** 耗时 (ms) */
    private long durationMs;

    /** 估算 Token */
    private int tokenEstimate;

    /** 估算成本 */
    private double costEstimate;

    /** 错误信息 */
    private String errorMessage;

    // ==================== Getter / Setter ====================

    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
    public String getActualOutput() { return actualOutput; }
    public void setActualOutput(String actualOutput) { this.actualOutput = actualOutput; }
    public List<String> getActualToolCalls() { return actualToolCalls; }
    public void setActualToolCalls(List<String> actualToolCalls) { this.actualToolCalls = actualToolCalls; }
    public List<String> getCheckDetails() { return checkDetails; }
    public void setCheckDetails(List<String> checkDetails) { this.checkDetails = checkDetails; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public int getTokenEstimate() { return tokenEstimate; }
    public void setTokenEstimate(int tokenEstimate) { this.tokenEstimate = tokenEstimate; }
    public double getCostEstimate() { return costEstimate; }
    public void setCostEstimate(double costEstimate) { this.costEstimate = costEstimate; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
