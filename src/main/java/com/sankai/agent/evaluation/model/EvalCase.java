package com.sankai.agent.evaluation.model;

import java.util.List;

/**
 * 评测用例。
 *
 * <p>定义一条评测输入和期望结果，用于回归测试。
 *
 * <h3>JSON 示例：</h3>
 * <pre>{@code
 * {
 *   "id": "TC-001",
 *   "category": "log_query",
 *   "input": "最近有什么 ERROR 日志？",
 *   "expectedToolCalls": ["query_logs"],
 *   "expectedKeywords": ["ERROR", "日志"],
 *   "description": "应调用 query_logs 工具查询 ERROR 级别日志"
 * }
 * }</pre>
 */
public class EvalCase {

    /** 用例 ID */
    private String id;

    /** 分类（log_query / ticket_query / mixed / chat） */
    private String category;

    /** 用户输入 */
    private String input;

    /** 期望调用的工具名称列表 */
    private List<String> expectedToolCalls;

    /** 回答中应包含的关键词 */
    private List<String> expectedKeywords;

    /** 用例描述 */
    private String description;

    /** 期望使用的模式（agent / orchestrate） */
    private String mode;

    // ==================== Getter / Setter ====================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
    public List<String> getExpectedToolCalls() { return expectedToolCalls; }
    public void setExpectedToolCalls(List<String> expectedToolCalls) { this.expectedToolCalls = expectedToolCalls; }
    public List<String> getExpectedKeywords() { return expectedKeywords; }
    public void setExpectedKeywords(List<String> expectedKeywords) { this.expectedKeywords = expectedKeywords; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
