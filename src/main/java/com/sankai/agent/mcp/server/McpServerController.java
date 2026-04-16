package com.sankai.agent.mcp.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sankai.agent.mcp.protocol.JsonRpcRequest;
import com.sankai.agent.mcp.protocol.JsonRpcResponse;
import com.sankai.agent.mcp.protocol.McpInitializeResult;
import com.sankai.agent.mcp.protocol.McpToolInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Server 核心控制器。
 *
 * <p>实现 MCP（Model Context Protocol）的 Streamable HTTP 传输层，
 * 通过标准的 HTTP POST 端点接收 JSON-RPC 2.0 请求，处理后返回 JSON-RPC 响应。
 *
 * <h3>MCP 协议架构：</h3>
 * <pre>{@code
 *                     JSON-RPC 2.0
 * MCP Client ─────── POST /mcp/rpc ───────> MCP Server
 *    (Agent)  <──── JSON-RPC Response ─────  (本控制器)
 *                                              │
 *                                    ┌─────────┼─────────┐
 *                                    ▼         ▼         ▼
 *                              query_database  query_logs  query_tickets
 * }</pre>
 *
 * <h3>支持的 MCP 方法：</h3>
 * <table>
 *   <tr><th>方法</th><th>说明</th></tr>
 *   <tr><td>{@code initialize}</td><td>初始化连接，返回服务器信息和能力声明</td></tr>
 *   <tr><td>{@code ping}</td><td>健康检查</td></tr>
 *   <tr><td>{@code tools/list}</td><td>列出所有可用工具及其参数 Schema</td></tr>
 *   <tr><td>{@code tools/call}</td><td>调用指定工具并返回结果</td></tr>
 * </table>
 *
 * <h3>请求示例：</h3>
 * <pre>{@code
 * curl -X POST http://localhost:8080/mcp/rpc \
 *   -H "Content-Type: application/json" \
 *   -H "Authorization: Bearer my-secret-token" \
 *   -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
 * }</pre>
 *
 * @see McpToolRegistry 工具注册中心
 * @see McpAuthFilter   鉴权过滤器
 */
@RestController
@RequestMapping("/mcp")
public class McpServerController {

    private static final Logger log = LoggerFactory.getLogger(McpServerController.class);

    private final McpToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    @Value("${mcp.server.name:agent-mcp-server}")
    private String serverName;

    @Value("${mcp.server.version:1.0.0}")
    private String serverVersion;

    public McpServerController(McpToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    // ==================== MCP 端点 ====================

    /**
     * MCP RPC 主端点 —— 接收所有 JSON-RPC 请求并路由到对应的处理方法。
     *
     * <p>这是 MCP Server 的唯一入口，所有操作都通过 {@code method} 字段来区分。
     * 这种设计遵循了 JSON-RPC 2.0 的"单端点多方法"模式。
     *
     * @param request JSON-RPC 2.0 请求体
     * @return JSON-RPC 2.0 响应体
     */
    @PostMapping(value = "/rpc",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonRpcResponse handleRpc(@RequestBody JsonRpcRequest request) {

        String method = request.getMethod();
        JsonNode id = request.getId();

        log.info("[MCP Server] 收到请求 - method: {}, id: {}", method, id);

        try {
            // 根据方法名路由到不同的处理逻辑
            return switch (method) {
                case "initialize" -> handleInitialize(id);
                case "ping" -> handlePing(id);
                case "tools/list" -> handleToolsList(id);
                case "tools/call" -> handleToolsCall(id, request.getParams());
                default -> {
                    log.warn("[MCP Server] 未知方法: {}", method);
                    yield JsonRpcResponse.error(id, JsonRpcResponse.METHOD_NOT_FOUND,
                            "不支持的方法: " + method);
                }
            };
        } catch (Exception e) {
            log.error("[MCP Server] 处理请求异常 - method: {}", method, e);
            return JsonRpcResponse.error(id, JsonRpcResponse.INTERNAL_ERROR,
                    "服务器内部错误: " + e.getMessage());
        }
    }

    /**
     * MCP Server 信息端点（非 RPC，用于浏览器查看服务状态）。
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "server", serverName,
                "version", serverVersion,
                "protocol", "MCP (Model Context Protocol)",
                "transport", "Streamable HTTP",
                "toolCount", toolRegistry.getToolCount(),
                "tools", toolRegistry.getToolNames()
        );
    }

    // ==================== MCP 方法实现 ====================

    /**
     * 处理 {@code initialize} 请求。
     *
     * <p>MCP 协议要求客户端在连接后首先发送 initialize 请求，
     * 服务器返回自身的协议版本、名称和支持的能力（capabilities）。
     * 客户端据此判断是否兼容，以及可以调用哪些功能。
     *
     * @param id 请求 ID
     * @return 包含服务器信息的初始化响应
     */
    private JsonRpcResponse handleInitialize(JsonNode id) {
        log.info("[MCP Server] 处理 initialize 请求");
        McpInitializeResult result = McpInitializeResult.defaultResult(serverName, serverVersion);
        return JsonRpcResponse.success(id, result);
    }

    /**
     * 处理 {@code ping} 请求。
     *
     * <p>简单的健康检查，返回空对象表示服务器正常运行。
     *
     * @param id 请求 ID
     * @return 空结果响应
     */
    private JsonRpcResponse handlePing(JsonNode id) {
        log.debug("[MCP Server] 处理 ping 请求");
        return JsonRpcResponse.success(id, Map.of());
    }

    /**
     * 处理 {@code tools/list} 请求。
     *
     * <p>返回所有已注册工具的列表，每个工具包含：
     * <ul>
     *   <li>name - 工具名称（调用时使用）</li>
     *   <li>description - 工具描述（LLM 用来理解用途）</li>
     *   <li>inputSchema - 参数 JSON Schema（LLM 用来构建参数）</li>
     * </ul>
     *
     * @param id 请求 ID
     * @return 工具列表响应
     */
    private JsonRpcResponse handleToolsList(JsonNode id) {
        log.info("[MCP Server] 处理 tools/list 请求");
        List<McpToolInfo> tools = toolRegistry.listTools();
        return JsonRpcResponse.success(id, McpToolInfo.wrapToolList(tools));
    }

    /**
     * 处理 {@code tools/call} 请求。
     *
     * <p>调用指定工具并返回结构化结果。MCP 协议要求 tools/call 的返回格式为：
     * <pre>{@code
     * {
     *   "content": [
     *     { "type": "text", "text": "JSON 格式的工具执行结果" }
     *   ],
     *   "isError": false
     * }
     * }</pre>
     *
     * @param id     请求 ID
     * @param params 请求参数（包含 name 和 arguments）
     * @return 工具调用结果响应
     */
    @SuppressWarnings("unchecked")
    private JsonRpcResponse handleToolsCall(JsonNode id, JsonNode params) {
        // 1. 提取工具名称
        if (params == null || !params.has("name")) {
            return JsonRpcResponse.error(id, JsonRpcResponse.INVALID_PARAMS,
                    "缺少必填参数: name（工具名称）");
        }

        String toolName = params.get("name").asText();

        // 2. 提取工具参数
        Map<String, Object> arguments = new HashMap<>();
        if (params.has("arguments")) {
            try {
                arguments = objectMapper.convertValue(params.get("arguments"), Map.class);
            } catch (Exception e) {
                return JsonRpcResponse.error(id, JsonRpcResponse.INVALID_PARAMS,
                        "参数解析失败: " + e.getMessage());
            }
        }

        // 3. 查找并调用工具
        log.info("[MCP Server] 调用工具: {}, 参数: {}", toolName, arguments);
        try {
            Object result = toolRegistry.invokeTool(toolName, arguments);
            String resultJson = objectMapper.writeValueAsString(result);

            // 4. 构建 MCP 标准的 tools/call 响应格式
            Map<String, Object> callResult = Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", resultJson
                    )),
                    "isError", false
            );

            return JsonRpcResponse.success(id, callResult);

        } catch (IllegalArgumentException e) {
            log.warn("[MCP Server] 工具调用参数错误: {}", e.getMessage());
            Map<String, Object> errorResult = Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "参数错误: " + e.getMessage()
                    )),
                    "isError", true
            );
            return JsonRpcResponse.success(id, errorResult);

        } catch (Exception e) {
            log.error("[MCP Server] 工具 {} 执行异常", toolName, e);
            Map<String, Object> errorResult = Map.of(
                    "content", List.of(Map.of(
                            "type", "text",
                            "text", "工具执行异常: " + e.getMessage()
                    )),
                    "isError", true
            );
            return JsonRpcResponse.success(id, errorResult);
        }
    }
}
