package com.sankai.agent.mcp.tool;

import com.sankai.agent.mcp.protocol.McpToolInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具 1：数据库查询工具。
 *
 * <p>提供对知识库（knowledge_segments 表）的关键词搜索能力。
 * Agent 可以通过此工具在已有的 RAG 知识库中搜索相关文档片段。
 *
 * <h3>支持的参数：</h3>
 * <ul>
 *   <li>{@code keyword} (必填) - 搜索关键词，将对 content 字段进行模糊匹配</li>
 *   <li>{@code limit}   (可选) - 返回结果数量上限，默认 5，最大 20</li>
 * </ul>
 *
 * <h3>调用示例：</h3>
 * <pre>{@code
 * { "name": "query_database", "arguments": { "keyword": "Spring Boot", "limit": 3 } }
 * }</pre>
 */
@Component
public class DatabaseQueryTool implements McpTool {

    private static final Logger log = LoggerFactory.getLogger(DatabaseQueryTool.class);

    /** 默认返回结果数量 */
    private static final int DEFAULT_LIMIT = 5;
    /** 最大返回结果数量 */
    private static final int MAX_LIMIT = 20;

    private final JdbcTemplate jdbcTemplate;

    public DatabaseQueryTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getName() {
        return "query_database";
    }

    /**
     * 返回此工具的描述和参数 Schema。
     * LLM 会根据此描述来理解何时该调用这个工具。
     */
    @Override
    public McpToolInfo getToolInfo() {
        // 构建 JSON Schema，描述工具接受的参数
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "keyword", Map.of(
                                "type", "string",
                                "description", "搜索关键词，将在知识库文档内容中进行模糊匹配"
                        ),
                        "limit", Map.of(
                                "type", "integer",
                                "description", "返回结果数量上限，默认 5，最大 20",
                                "default", DEFAULT_LIMIT
                        )
                ),
                "required", List.of("keyword")
        );

        return new McpToolInfo(
                getName(),
                "查询知识库数据库：在已索引的文档片段中搜索与关键词匹配的内容，返回匹配的文档片段和元数据",
                schema
        );
    }

    /**
     * 执行数据库查询。
     *
     * <p>使用 SQL LIKE 进行模糊匹配，按创建时间倒序返回。
     * 每条结果包含 content（内容）和 metadata（元数据）。
     *
     * @param arguments 包含 keyword 和可选 limit 的参数
     * @return 查询结果列表
     */
    @Override
    public Object execute(Map<String, Object> arguments) {
        // 1. 参数校验与提取
        String keyword = extractString(arguments, "keyword");
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("参数 keyword 不能为空");
        }
        int limit = extractInt(arguments, "limit", DEFAULT_LIMIT);
        limit = Math.min(Math.max(limit, 1), MAX_LIMIT); // 限制在 [1, 20] 范围内

        log.info("[查库工具] 搜索关键词: {}, 限制: {}", keyword, limit);

        // 2. 执行模糊查询
        String sql = """
                SELECT content, metadata::text AS metadata, created_at
                FROM knowledge_segments
                WHERE content ILIKE ?
                ORDER BY created_at DESC
                LIMIT ?
                """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, "%" + keyword + "%", limit);

        // 3. 组装结构化结果
        List<Map<String, Object>> results = rows.stream()
                .map(row -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("content", truncate((String) row.get("content"), 300));
                    item.put("metadata", row.get("metadata"));
                    item.put("created_at", String.valueOf(row.get("created_at")));
                    return item;
                })
                .toList();

        return Map.of(
                "tool", getName(),
                "total", results.size(),
                "keyword", keyword,
                "results", results
        );
    }

    // ==================== 私有辅助方法 ====================

    /** 从参数 Map 中提取字符串 */
    private String extractString(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val != null ? val.toString() : null;
    }

    /** 从参数 Map 中提取整数，提供默认值 */
    private int extractInt(Map<String, Object> args, String key, int defaultValue) {
        Object val = args.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 截断过长文本 */
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
