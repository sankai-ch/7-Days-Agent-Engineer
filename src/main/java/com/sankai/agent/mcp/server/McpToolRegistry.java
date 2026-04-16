package com.sankai.agent.mcp.server;

import com.sankai.agent.mcp.protocol.McpToolInfo;
import com.sankai.agent.mcp.tool.McpTool;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP 工具注册中心。
 *
 * <p>利用 Spring 的依赖注入，自动收集所有实现了 {@link McpTool} 接口的 Bean，
 * 并将它们注册到统一的注册表中。其他组件可以通过此注册中心：
 * <ul>
 *   <li>列出所有可用工具 {@link #listTools()}</li>
 *   <li>按名称查找工具 {@link #findTool(String)}</li>
 *   <li>调用指定工具 {@link #invokeTool(String, Map)}</li>
 * </ul>
 *
 * <p>设计模式：<b>注册表模式（Registry Pattern）</b>
 * <p>这是一种典型的"插件式架构"——新增工具只需要实现 McpTool 接口并加上 @Component 注解，
 * 无需修改注册中心或服务端的任何代码，满足<b>开闭原则</b>。
 */
@Component
public class McpToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistry.class);

    /**
     * 工具注册表：name -> McpTool 实例。
     * 使用 LinkedHashMap 保持工具的注册顺序。
     */
    private final Map<String, McpTool> toolMap = new LinkedHashMap<>();

    /** Spring 自动注入所有实现了 McpTool 接口的 Bean */
    private final List<McpTool> tools;

    public McpToolRegistry(List<McpTool> tools) {
        this.tools = tools;
    }

    /**
     * 应用启动后自动注册所有工具。
     * <p>遍历 Spring 容器中所有 McpTool 实例，以工具名称为 key 存入注册表。
     */
    @PostConstruct
    public void init() {
        for (McpTool tool : tools) {
            String name = tool.getName();
            if (toolMap.containsKey(name)) {
                log.warn("[MCP] 工具名称冲突，跳过重复注册: {}", name);
                continue;
            }
            toolMap.put(name, tool);
            log.info("[MCP] 注册工具: {} - {}", name, tool.getToolInfo().getDescription());
        }
        log.info("[MCP] 工具注册完成，共 {} 个工具可用", toolMap.size());
    }

    /**
     * 列出所有已注册工具的描述信息。
     *
     * @return 工具信息列表（不可变）
     */
    public List<McpToolInfo> listTools() {
        return toolMap.values().stream()
                .map(McpTool::getToolInfo)
                .toList();
    }

    /**
     * 按名称查找工具。
     *
     * @param name 工具名称
     * @return 工具实例（可能为空）
     */
    public Optional<McpTool> findTool(String name) {
        return Optional.ofNullable(toolMap.get(name));
    }

    /**
     * 调用指定名称的工具。
     *
     * @param name      工具名称
     * @param arguments 调用参数
     * @return 工具执行结果
     * @throws IllegalArgumentException 如果工具不存在
     */
    public Object invokeTool(String name, Map<String, Object> arguments) {
        McpTool tool = toolMap.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("未找到工具: " + name);
        }

        log.info("[MCP] 调用工具: {}, 参数: {}", name, arguments);
        try {
            Object result = tool.execute(arguments);
            log.info("[MCP] 工具 {} 执行成功", name);
            return result;
        } catch (Exception e) {
            log.error("[MCP] 工具 {} 执行失败: {}", name, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 获取已注册工具数量。
     *
     * @return 工具总数
     */
    public int getToolCount() {
        return toolMap.size();
    }

    /**
     * 获取所有已注册工具的名称。
     *
     * @return 工具名称集合（不可变）
     */
    public java.util.Set<String> getToolNames() {
        return Collections.unmodifiableSet(toolMap.keySet());
    }
}
