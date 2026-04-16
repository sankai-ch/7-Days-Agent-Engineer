package com.sankai.agent.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 请求模型。
 *
 * <p>MCP（Model Context Protocol）基于 JSON-RPC 2.0 协议进行通信，
 * 每条请求都包含一个方法名（method）和可选的参数（params）。
 *
 * <p>典型的 MCP 请求示例：
 * <pre>{@code
 * {
 *   "jsonrpc": "2.0",
 *   "id": 1,
 *   "method": "tools/call",
 *   "params": { "name": "query_database", "arguments": { "sql": "..." } }
 * }
 * }</pre>
 *
 * @see <a href="https://www.jsonrpc.org/specification">JSON-RPC 2.0 Specification</a>
 * @see <a href="https://modelcontextprotocol.io">MCP Protocol Specification</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonRpcRequest {

    /** JSON-RPC 协议版本，固定为 "2.0" */
    private String jsonrpc = "2.0";

    /**
     * 请求唯一标识符。
     * 使用 JsonNode 类型以兼容 int、string、null 等多种 ID 格式。
     */
    private JsonNode id;

    /**
     * 请求方法名，对应 MCP 协议定义的操作。
     * <ul>
     *   <li>{@code initialize} - 初始化连接</li>
     *   <li>{@code tools/list} - 列出可用工具</li>
     *   <li>{@code tools/call} - 调用指定工具</li>
     *   <li>{@code ping} - 健康检查</li>
     * </ul>
     */
    private String method;

    /** 请求参数，不同方法对应不同的参数结构 */
    private JsonNode params;

    // ==================== Getter / Setter ====================

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public JsonNode getId() {
        return id;
    }

    public void setId(JsonNode id) {
        this.id = id;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public JsonNode getParams() {
        return params;
    }

    public void setParams(JsonNode params) {
        this.params = params;
    }
}
