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
 * MCP 工具 2：日志查询工具。
 *
 * <p>提供对应用日志（app_logs 表）的查询能力。
 * Agent 可以通过此工具按日志级别、服务名称、关键词等条件搜索应用运行日志。
 *
 * <h3>典型使用场景：</h3>
 * <ul>
 *   <li>"最近有什么 ERROR 日志？" → 按级别过滤</li>
 *   <li>"user-service 的最新日志" → 按服务名过滤</li>
 *   <li>"搜索包含 timeout 的日志" → 按关键词搜索</li>
 * </ul>
 *
 * <h3>支持的参数：</h3>
 * <ul>
 *   <li>{@code level}   (可选) - 日志级别：INFO / WARN / ERROR</li>
 *   <li>{@code service} (可选) - 服务名称</li>
 *   <li>{@code keyword} (可选) - 日志内容关键词</li>
 *   <li>{@code limit}   (可选) - 返回数量上限，默认 10</li>
 * </ul>
 */
@Component
public class LogQueryTool implements McpTool {

    private static final Logger log = LoggerFactory.getLogger(LogQueryTool.class);
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final JdbcTemplate jdbcTemplate;

    public LogQueryTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getName() {
        return "query_logs";
    }

    @Override
    public McpToolInfo getToolInfo() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "level", Map.of(
                                "type", "string",
                                "description", "日志级别过滤：INFO、WARN、ERROR",
                                "enum", List.of("INFO", "WARN", "ERROR")
                        ),
                        "service", Map.of(
                                "type", "string",
                                "description", "按服务名称过滤，如 user-service、order-service"
                        ),
                        "keyword", Map.of(
                                "type", "string",
                                "description", "在日志消息内容中搜索的关键词"
                        ),
                        "limit", Map.of(
                                "type", "integer",
                                "description", "返回结果数量上限，默认 10，最大 50",
                                "default", DEFAULT_LIMIT
                        )
                )
                // 注意：所有参数都是可选的，不设置 required
        );

        return new McpToolInfo(
                getName(),
                "查询应用日志：按级别、服务名、关键词搜索应用运行日志，用于问题排查和运维监控",
                schema
        );
    }

    /**
     * 执行日志查询。
     *
     * <p>根据传入的过滤条件动态构建 SQL 查询，支持多条件组合。
     * 使用参数化查询防止 SQL 注入。
     *
     * @param arguments 查询参数
     * @return 结构化的日志查询结果
     */
    @Override
    public Object execute(Map<String, Object> arguments) {
        // 1. 提取查询参数
        String level = extractString(arguments, "level");
        String service = extractString(arguments, "service");
        String keyword = extractString(arguments, "keyword");
        int limit = extractInt(arguments, "limit", DEFAULT_LIMIT);
        limit = Math.min(Math.max(limit, 1), MAX_LIMIT);

        log.info("[日志工具] 查询条件 - level:{}, service:{}, keyword:{}, limit:{}",
                level, service, keyword, limit);

        // 2. 动态构建 SQL（使用 StringBuilder 拼接 WHERE 条件）
        StringBuilder sql = new StringBuilder(
                "SELECT id, level, service, message, trace_id, created_at FROM app_logs WHERE 1=1");
        java.util.List<Object> params = new java.util.ArrayList<>();

        if (level != null && !level.isBlank()) {
            sql.append(" AND level = ?");
            params.add(level.toUpperCase());
        }
        if (service != null && !service.isBlank()) {
            sql.append(" AND service ILIKE ?");
            params.add("%" + service + "%");
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND message ILIKE ?");
            params.add("%" + keyword + "%");
        }

        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(limit);

        // 3. 执行查询
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql.toString(), params.toArray());

        // 4. 格式化结果
        List<Map<String, Object>> results = rows.stream()
                .map(row -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.get("id"));
                    item.put("level", row.get("level"));
                    item.put("service", row.get("service"));
                    item.put("message", row.get("message"));
                    item.put("trace_id", row.get("trace_id"));
                    item.put("time", String.valueOf(row.get("created_at")));
                    return item;
                })
                .toList();

        return Map.of(
                "tool", getName(),
                "total", results.size(),
                "filters", Map.of(
                        "level", level != null ? level : "ALL",
                        "service", service != null ? service : "ALL",
                        "keyword", keyword != null ? keyword : ""
                ),
                "results", results
        );
    }

    // ==================== 私有辅助方法 ====================

    private String extractString(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val != null ? val.toString() : null;
    }

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
}
