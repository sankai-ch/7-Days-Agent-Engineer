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
 * MCP 工具 3：工单查询工具。
 *
 * <p>提供对工单系统（work_tickets 表）的查询能力。
 * Agent 可以通过此工具按状态、优先级、负责人等条件查询工单。
 *
 * <h3>典型使用场景：</h3>
 * <ul>
 *   <li>"有哪些未解决的高优先级工单？" → 按状态 + 优先级过滤</li>
 *   <li>"张三负责的工单进度如何？" → 按 assignee 查询</li>
 *   <li>"搜索关于登录问题的工单" → 按关键词搜索</li>
 * </ul>
 *
 * <h3>支持的参数：</h3>
 * <ul>
 *   <li>{@code status}   (可选) - 工单状态：OPEN / IN_PROGRESS / RESOLVED / CLOSED</li>
 *   <li>{@code priority} (可选) - 优先级：LOW / MEDIUM / HIGH / CRITICAL</li>
 *   <li>{@code assignee} (可选) - 负责人姓名</li>
 *   <li>{@code keyword}  (可选) - 标题或描述中的关键词</li>
 *   <li>{@code limit}    (可选) - 返回数量上限，默认 10</li>
 * </ul>
 */
@Component
public class TicketQueryTool implements McpTool {

    private static final Logger log = LoggerFactory.getLogger(TicketQueryTool.class);
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final JdbcTemplate jdbcTemplate;

    public TicketQueryTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getName() {
        return "query_tickets";
    }

    @Override
    public McpToolInfo getToolInfo() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "status", Map.of(
                                "type", "string",
                                "description", "工单状态过滤",
                                "enum", List.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED")
                        ),
                        "priority", Map.of(
                                "type", "string",
                                "description", "工单优先级过滤",
                                "enum", List.of("LOW", "MEDIUM", "HIGH", "CRITICAL")
                        ),
                        "assignee", Map.of(
                                "type", "string",
                                "description", "工单负责人姓名"
                        ),
                        "keyword", Map.of(
                                "type", "string",
                                "description", "在工单标题和描述中搜索的关键词"
                        ),
                        "limit", Map.of(
                                "type", "integer",
                                "description", "返回结果数量上限，默认 10，最大 50",
                                "default", DEFAULT_LIMIT
                        )
                )
        );

        return new McpToolInfo(
                getName(),
                "查询工单系统：按状态、优先级、负责人、关键词搜索工单，用于项目管理和问题追踪",
                schema
        );
    }

    /**
     * 执行工单查询。
     *
     * <p>支持多维度组合过滤，按更新时间倒序返回。
     *
     * @param arguments 查询参数
     * @return 结构化的工单查询结果
     */
    @Override
    public Object execute(Map<String, Object> arguments) {
        // 1. 提取查询参数
        String status = extractString(arguments, "status");
        String priority = extractString(arguments, "priority");
        String assignee = extractString(arguments, "assignee");
        String keyword = extractString(arguments, "keyword");
        int limit = extractInt(arguments, "limit", DEFAULT_LIMIT);
        limit = Math.min(Math.max(limit, 1), MAX_LIMIT);

        log.info("[工单工具] 查询条件 - status:{}, priority:{}, assignee:{}, keyword:{}, limit:{}",
                status, priority, assignee, keyword, limit);

        // 2. 动态构建 SQL
        StringBuilder sql = new StringBuilder(
                "SELECT id, title, description, status, priority, assignee, created_at, updated_at "
                + "FROM work_tickets WHERE 1=1");
        java.util.List<Object> params = new java.util.ArrayList<>();

        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status.toUpperCase());
        }
        if (priority != null && !priority.isBlank()) {
            sql.append(" AND priority = ?");
            params.add(priority.toUpperCase());
        }
        if (assignee != null && !assignee.isBlank()) {
            sql.append(" AND assignee ILIKE ?");
            params.add("%" + assignee + "%");
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (title ILIKE ? OR description ILIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }

        sql.append(" ORDER BY updated_at DESC LIMIT ?");
        params.add(limit);

        // 3. 执行查询
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql.toString(), params.toArray());

        // 4. 格式化结果
        List<Map<String, Object>> results = rows.stream()
                .map(row -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", row.get("id"));
                    item.put("title", row.get("title"));
                    item.put("description", truncate((String) row.get("description"), 200));
                    item.put("status", row.get("status"));
                    item.put("priority", row.get("priority"));
                    item.put("assignee", row.get("assignee"));
                    item.put("created_at", String.valueOf(row.get("created_at")));
                    item.put("updated_at", String.valueOf(row.get("updated_at")));
                    return item;
                })
                .toList();

        return Map.of(
                "tool", getName(),
                "total", results.size(),
                "filters", Map.of(
                        "status", status != null ? status : "ALL",
                        "priority", priority != null ? priority : "ALL",
                        "assignee", assignee != null ? assignee : "ALL",
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

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
