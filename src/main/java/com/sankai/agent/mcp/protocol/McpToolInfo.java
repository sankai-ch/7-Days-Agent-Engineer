package com.sankai.agent.mcp.protocol;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具信息描述模型。
 *
 * <p>当客户端发送 {@code tools/list} 请求时，MCP Server 返回此对象的列表，
 * 描述每个工具的名称、功能和参数 schema，客户端（或 LLM）据此决定调用哪个工具。
 *
 * <p>遵循 MCP 协议中 Tool 的定义格式：
 * <pre>{@code
 * {
 *   "name": "query_database",
 *   "description": "查询知识库中的文档片段",
 *   "inputSchema": {
 *     "type": "object",
 *     "properties": { "keyword": { "type": "string", "description": "搜索关键词" } },
 *     "required": ["keyword"]
 *   }
 * }
 * }</pre>
 */
public class McpToolInfo {

    /** 工具唯一名称（英文标识符，如 query_database） */
    private String name;

    /** 工具功能描述（供 LLM 理解工具用途） */
    private String description;

    /**
     * 工具输入参数的 JSON Schema。
     * <p>遵循 JSON Schema 规范，定义工具接受的参数类型和约束。
     */
    private Map<String, Object> inputSchema;

    public McpToolInfo() {}

    public McpToolInfo(String name, String description, Map<String, Object> inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    // ==================== Getter / Setter ====================

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getInputSchema() { return inputSchema; }
    public void setInputSchema(Map<String, Object> inputSchema) { this.inputSchema = inputSchema; }

    // ==================== 构建工具列表响应 ====================

    /**
     * 构建 tools/list 的响应数据结构。
     *
     * @param tools 工具信息列表
     * @return MCP 协议规定的 tools 列表格式 {@code {"tools": [...]}}
     */
    public static Map<String, Object> wrapToolList(List<McpToolInfo> tools) {
        return Map.of("tools", tools);
    }
}
