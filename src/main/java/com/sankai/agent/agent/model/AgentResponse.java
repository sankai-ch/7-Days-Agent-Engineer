package com.sankai.agent.agent.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 响应模型。
 *
 * <p>包含 Agent 的最终回答、调用的工具链记录和思考过程，
 * 提供完整的可观测性（observability），方便调试和复盘。
 *
 * <p>响应示例：
 * <pre>{@code
 * {
 *   "answer": "当前有 3 个高优先级未解决工单...",
 *   "toolCalls": [
 *     { "tool": "query_tickets", "arguments": {...}, "result": {...}, "durationMs": 120 }
 *   ],
 *   "thinking": "用户想了解高优先级未解决工单，需要调用工单查询工具...",
 *   "success": true
 * }
 * }</pre>
 */
public class AgentResponse {

    /** Agent 最终给用户的自然语言回答 */
    private String answer;

    /** 工具调用记录链（按调用顺序排列） */
    private List<ToolCallRecord> toolCalls = new ArrayList<>();

    /** Agent 的思考过程描述（LLM 的推理轨迹） */
    private String thinking;

    /** 是否成功完成 */
    private boolean success;

    /** 错误信息（仅在 success=false 时有值） */
    private String errorMessage;

    // ==================== 静态工厂方法 ====================

    /**
     * 构建成功响应。
     */
    public static AgentResponse success(String answer, List<ToolCallRecord> toolCalls, String thinking) {
        AgentResponse resp = new AgentResponse();
        resp.setAnswer(answer);
        resp.setToolCalls(toolCalls);
        resp.setThinking(thinking);
        resp.setSuccess(true);
        return resp;
    }

    /**
     * 构建失败响应。
     */
    public static AgentResponse failure(String errorMessage) {
        AgentResponse resp = new AgentResponse();
        resp.setSuccess(false);
        resp.setErrorMessage(errorMessage);
        resp.setAnswer("抱歉，处理您的请求时出现了问题: " + errorMessage);
        return resp;
    }

    // ==================== 工具调用记录 ====================

    /**
     * 单次工具调用的完整记录。
     * 记录了调用了哪个工具、传入了什么参数、返回了什么结果、耗时多少。
     */
    public static class ToolCallRecord {

        /** 调用的工具名称 */
        private String tool;

        /** 传入的参数 */
        private Map<String, Object> arguments;

        /** 工具返回的结果 */
        private Object result;

        /** 调用耗时（毫秒） */
        private long durationMs;

        /** 是否调用成功 */
        private boolean success;

        public ToolCallRecord() {}

        public ToolCallRecord(String tool, Map<String, Object> arguments,
                              Object result, long durationMs, boolean success) {
            this.tool = tool;
            this.arguments = arguments;
            this.result = result;
            this.durationMs = durationMs;
            this.success = success;
        }

        // Getter / Setter
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
    }

    // ==================== Getter / Setter ====================

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public List<ToolCallRecord> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCallRecord> toolCalls) { this.toolCalls = toolCalls; }
    public String getThinking() { return thinking; }
    public void setThinking(String thinking) { this.thinking = thinking; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
