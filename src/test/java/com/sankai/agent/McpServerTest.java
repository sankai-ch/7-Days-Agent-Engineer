package com.sankai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sankai.agent.mcp.protocol.JsonRpcRequest;
import com.sankai.agent.mcp.protocol.JsonRpcResponse;
import com.sankai.agent.mcp.protocol.McpToolInfo;
import com.sankai.agent.mcp.server.McpServerController;
import com.sankai.agent.mcp.server.McpToolRegistry;
import com.sankai.agent.mcp.tool.McpTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MCP Server 集成测试。
 *
 * <p>测试 MCP 协议的核心流程：
 * <ul>
 *   <li>initialize - 初始化连接</li>
 *   <li>ping - 健康检查</li>
 *   <li>tools/list - 列出可用工具</li>
 *   <li>tools/call - 调用工具</li>
 *   <li>鉴权机制</li>
 *   <li>错误处理</li>
 * </ul>
 */
@WebMvcTest(McpServerController.class)
class McpServerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** 测试用鉴权 Token */
    private static final String AUTH_TOKEN = "test-token-12345";
    private static final String AUTH_HEADER = "Bearer " + AUTH_TOKEN;

    /**
     * 测试配置：注入 Mock 工具。
     * 使用一个简单的 echo 工具来验证 MCP 协议流程。
     */
    @TestConfiguration
    static class McpTestConfig {

        @Bean
        public McpToolRegistry mcpToolRegistry(List<McpTool> tools) {
            return new McpToolRegistry(tools);
        }

        /** 测试用的 echo 工具——原样返回输入参数 */
        @Bean
        public McpTool echoTool() {
            return new McpTool() {
                @Override
                public String getName() {
                    return "echo";
                }

                @Override
                public McpToolInfo getToolInfo() {
                    return new McpToolInfo(
                            "echo",
                            "测试工具：原样返回输入参数",
                            Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "message", Map.of("type", "string", "description", "回显的消息")
                                    ),
                                    "required", List.of("message")
                            )
                    );
                }

                @Override
                public Object execute(Map<String, Object> arguments) {
                    return Map.of("echo", arguments.getOrDefault("message", ""));
                }
            };
        }
    }

    // ==================== 初始化测试 ====================

    @Test
    @DisplayName("initialize - 应返回服务器信息和能力声明")
    void testInitialize() throws Exception {
        String request = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                """;

        mockMvc.perform(post("/mcp/rpc")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.result.protocolVersion").value("2024-11-05"))
                .andExpect(jsonPath("$.result.serverInfo.name").value("test-mcp-server"))
                .andExpect(jsonPath("$.result.capabilities.tools").exists());
    }

    // ==================== Ping 测试 ====================

    @Test
    @DisplayName("ping - 应返回空结果表示健康")
    void testPing() throws Exception {
        String request = """
                {"jsonrpc":"2.0","id":2,"method":"ping"}
                """;

        mockMvc.perform(post("/mcp/rpc")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").isMap())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    // ==================== tools/list 测试 ====================

    @Test
    @DisplayName("tools/list - 应返回已注册的工具列表")
    void testToolsList() throws Exception {
        String request = """
                {"jsonrpc":"2.0","id":3,"method":"tools/list"}
                """;

        mockMvc.perform(post("/mcp/rpc")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools").isArray())
                .andExpect(jsonPath("$.result.tools", hasSize(1)))
                .andExpect(jsonPath("$.result.tools[0].name").value("echo"))
                .andExpect(jsonPath("$.result.tools[0].description").exists())
                .andExpect(jsonPath("$.result.tools[0].inputSchema").exists());
    }

    // ==================== tools/call 测试 ====================

    @Test
    @DisplayName("tools/call - 应成功调用工具并返回结构化结果")
    void testToolsCall() throws Exception {
        String request = """
                {
                  "jsonrpc":"2.0",
                  "id":4,
                  "method":"tools/call",
                  "params":{
                    "name":"echo",
                    "arguments":{"message":"Hello MCP!"}
                  }
                }
                """;

        mockMvc.perform(post("/mcp/rpc")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.content").isArray())
                .andExpect(jsonPath("$.result.content[0].type").value("text"))
                .andExpect(jsonPath("$.result.content[0].text", containsString("Hello MCP!")));
    }

    @Test
    @DisplayName("tools/call - 调用不存在的工具应返回错误标记")
    void testToolsCallNotFound() throws Exception {
        String request = """
                {
                  "jsonrpc":"2.0",
                  "id":5,
                  "method":"tools/call",
                  "params":{"name":"non_existent_tool","arguments":{}}
                }
                """;

        mockMvc.perform(post("/mcp/rpc")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(true))
                .andExpect(jsonPath("$.result.content[0].text", containsString("未找到工具")));
    }

    @Test
    @DisplayName("tools/call - 缺少 name 参数应返回错误")
    void testToolsCallMissingName() throws Exception {
        String request = """
                {
                  "jsonrpc":"2.0",
                  "id":6,
                  "method":"tools/call",
                  "params":{"arguments":{}}
                }
                """;

        mockMvc.perform(post("/mcp/rpc")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error.code").value(-32602));
    }

    // ==================== 未知方法测试 ====================

    @Test
    @DisplayName("unknown method - 应返回 METHOD_NOT_FOUND 错误")
    void testUnknownMethod() throws Exception {
        String request = """
                {"jsonrpc":"2.0","id":7,"method":"unknown/method"}
                """;

        mockMvc.perform(post("/mcp/rpc")
                        .header("Authorization", AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32601))
                .andExpect(jsonPath("$.error.message", containsString("不支持的方法")));
    }

    // ==================== 鉴权测试 ====================

    @Test
    @DisplayName("auth - 无 Token 时应返回 401")
    void testAuthNoToken() throws Exception {
        String request = """
                {"jsonrpc":"2.0","id":8,"method":"ping"}
                """;

        mockMvc.perform(post("/mcp/rpc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("auth - 错误 Token 时应返回 401")
    void testAuthWrongToken() throws Exception {
        String request = """
                {"jsonrpc":"2.0","id":9,"method":"ping"}
                """;

        mockMvc.perform(post("/mcp/rpc")
                        .header("Authorization", "Bearer wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized());
    }

    // ==================== 信息端点测试 ====================

    @Test
    @DisplayName("GET /mcp/info - 应返回服务器状态信息")
    void testInfoEndpoint() throws Exception {
        mockMvc.perform(get("/mcp/info")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.server").value("test-mcp-server"))
                .andExpect(jsonPath("$.protocol").value("MCP (Model Context Protocol)"))
                .andExpect(jsonPath("$.toolCount").value(1));
    }
}
