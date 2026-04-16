package com.sankai.agent.mcp.tool;

import com.sankai.agent.mcp.protocol.McpToolInfo;

import java.util.Map;

/**
 * MCP 工具统一接口。
 *
 * <p>所有 MCP 工具都必须实现此接口。通过此接口实现"面向接口编程"，
 * 使得工具的注册、发现和调用完全解耦：
 * <ul>
 *   <li>{@link #getName()} - 工具唯一标识，客户端用此名称调用</li>
 *   <li>{@link #getToolInfo()} - 返回工具的完整描述（含参数 Schema），供 LLM 理解</li>
 *   <li>{@link #execute(Map)} - 实际执行工具逻辑</li>
 * </ul>
 *
 * <h3>实现新工具的步骤：</h3>
 * <ol>
 *   <li>创建一个新类实现 {@code McpTool} 接口</li>
 *   <li>标注 {@code @Component} 让 Spring 自动管理</li>
 *   <li>实现三个方法</li>
 *   <li>工具会被 {@link com.sankai.agent.mcp.server.McpToolRegistry} 自动发现和注册</li>
 * </ol>
 *
 * <h3>示例：</h3>
 * <pre>{@code
 * @Component
 * public class MyTool implements McpTool {
 *     public String getName() { return "my_tool"; }
 *     public McpToolInfo getToolInfo() { ... }
 *     public Object execute(Map<String, Object> arguments) { ... }
 * }
 * }</pre>
 */
public interface McpTool {

    /**
     * 工具唯一名称。
     * <p>命名规范：使用 snake_case，例如 {@code query_database}。
     *
     * @return 工具名称字符串
     */
    String getName();

    /**
     * 获取工具的完整描述信息（含 JSON Schema 参数定义）。
     * <p>此信息会在 {@code tools/list} 请求时返回给客户端，
     * LLM 据此理解工具的用途和参数格式。
     *
     * @return 工具信息对象
     */
    McpToolInfo getToolInfo();

    /**
     * 执行工具逻辑。
     *
     * @param arguments 客户端传入的参数（key-value 键值对）
     * @return 执行结果，将被序列化为 JSON 返回给客户端
     * @throws IllegalArgumentException 如果参数不合法
     * @throws RuntimeException         如果执行过程中发生错误
     */
    Object execute(Map<String, Object> arguments);
}
