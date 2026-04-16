package com.sankai.agent.orchestration.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 执行计划步骤。
 *
 * <p>由 Planner 角色生成，描述单步操作：需要调用哪个工具、传什么参数、
 * 这一步的目的是什么。Executor 按顺序消费这些步骤。
 *
 * <h3>示例：</h3>
 * <pre>{@code
 * {
 *   "stepId": 1,
 *   "tool": "query_tickets",
 *   "arguments": {"status": "OPEN", "priority": "HIGH"},
 *   "purpose": "查询所有高优先级未解决工单",
 *   "dependsOn": []
 * }
 * }</pre>
 */
public class PlanStep {

    /** 步骤序号（从 1 开始） */
    private int stepId;

    /** 需要调用的工具名称 */
    private String tool;

    /** 工具调用参数 */
    private Map<String, Object> arguments;

    /** 这一步的目的/意图描述 */
    private String purpose;

    /** 依赖的前置步骤 ID 列表（空表示无依赖，可并行） */
    private List<Integer> dependsOn = new ArrayList<>();

    public PlanStep() {}

    public PlanStep(int stepId, String tool, Map<String, Object> arguments, String purpose) {
        this.stepId = stepId;
        this.tool = tool;
        this.arguments = arguments;
        this.purpose = purpose;
    }

    // ==================== Getter / Setter ====================

    public int getStepId() { return stepId; }
    public void setStepId(int stepId) { this.stepId = stepId; }

    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }

    public Map<String, Object> getArguments() { return arguments; }
    public void setArguments(Map<String, Object> arguments) { this.arguments = arguments; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public List<Integer> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<Integer> dependsOn) { this.dependsOn = dependsOn; }
}
