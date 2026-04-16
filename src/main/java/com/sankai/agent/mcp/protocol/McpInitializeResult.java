package com.sankai.agent.mcp.protocol;

import java.util.Map;

/**
 * MCP 初始化响应结果。
 *
 * <p>当客户端发送 {@code initialize} 请求时，服务端返回此对象，
 * 告知客户端服务器的协议版本、名称和支持的能力（capabilities）。
 *
 * <p>响应示例：
 * <pre>{@code
 * {
 *   "protocolVersion": "2024-11-05",
 *   "serverInfo": { "name": "agent-mcp-server", "version": "1.0.0" },
 *   "capabilities": { "tools": { "listChanged": false } }
 * }
 * }</pre>
 */
public class McpInitializeResult {

    /** MCP 协议版本号 */
    private String protocolVersion;

    /** 服务器基本信息 */
    private Map<String, String> serverInfo;

    /** 服务器支持的能力声明 */
    private Map<String, Object> capabilities;

    public McpInitializeResult() {}

    /**
     * 创建默认的初始化结果。
     *
     * @param serverName    服务器名称
     * @param serverVersion 服务器版本号
     * @return 初始化结果实例
     */
    public static McpInitializeResult defaultResult(String serverName, String serverVersion) {
        McpInitializeResult result = new McpInitializeResult();
        result.setProtocolVersion("2024-11-05");
        result.setServerInfo(Map.of(
                "name", serverName,
                "version", serverVersion
        ));
        // 声明服务器支持 tools 能力
        result.setCapabilities(Map.of(
                "tools", Map.of("listChanged", false)
        ));
        return result;
    }

    // ==================== Getter / Setter ====================

    public String getProtocolVersion() { return protocolVersion; }
    public void setProtocolVersion(String protocolVersion) { this.protocolVersion = protocolVersion; }

    public Map<String, String> getServerInfo() { return serverInfo; }
    public void setServerInfo(Map<String, String> serverInfo) { this.serverInfo = serverInfo; }

    public Map<String, Object> getCapabilities() { return capabilities; }
    public void setCapabilities(Map<String, Object> capabilities) { this.capabilities = capabilities; }
}
