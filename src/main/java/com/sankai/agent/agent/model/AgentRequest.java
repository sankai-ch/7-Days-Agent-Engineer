package com.sankai.agent.agent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Agent 请求模型。
 *
 * <p>用户通过自然语言向 Agent 发起请求，Agent 解析意图后决定调用哪些工具。
 *
 * <p>请求示例：
 * <pre>{@code
 * {
 *   "query": "最近有什么高优先级的未解决工单？",
 *   "conversationId": "session-001"   // 可选，用于关联同一会话
 * }
 * }</pre>
 */
public class AgentRequest {

    /** 用户的自然语言查询 */
    @NotBlank(message = "查询内容不能为空")
    @Size(min = 2, max = 2000, message = "查询长度需在 2-2000 字符之间")
    private String query;

    /** 可选的会话 ID，用于关联上下文（预留扩展） */
    private String conversationId;

    // ==================== Getter / Setter ====================

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
}
