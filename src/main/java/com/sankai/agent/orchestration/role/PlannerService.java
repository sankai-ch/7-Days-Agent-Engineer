package com.sankai.agent.orchestration.role;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sankai.agent.mcp.protocol.McpToolInfo;
import com.sankai.agent.mcp.server.McpToolRegistry;
import com.sankai.agent.orchestration.model.PlanStep;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Planner（规划者）角色 —— 编排流水线第一阶段。
 *
 * <p>负责将用户的复杂自然语言任务拆解为<b>可执行的多步骤计划</b>。
 * 每一步指定需要调用的工具、参数和目的。
 *
 * <h3>核心职责：</h3>
 * <ol>
 *   <li>理解用户意图，判断任务复杂度</li>
 *   <li>将复杂任务分解为多个原子步骤</li>
 *   <li>为每一步选择合适的工具和参数</li>
 *   <li>确定步骤间的依赖关系（顺序 vs 并行）</li>
 * </ol>
 *
 * <h3>工作流：</h3>
 * <pre>{@code
 * 用户任务: "分析当前系统的高优工单情况及相关日志"
 *     │
 *     ▼
 * [Planner LLM 推理]
 *     │
 *     ▼
 * Plan:
 *   Step 1: query_tickets {status:OPEN, priority:HIGH}  → 查高优工单
 *   Step 2: query_tickets {status:OPEN, priority:CRITICAL} → 查紧急工单
 *   Step 3: query_logs {level:ERROR, limit:10}  → 查最近错误日志
 * }</pre>
 */
@Service
public class PlannerService {

    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);

    private final QwenChatModel chatModel;
    private final McpToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public PlannerService(QwenChatModel chatModel,
                          McpToolRegistry toolRegistry,
                          ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * 为用户任务生成执行计划。
     *
     * <p>调用 LLM 分析任务，输出结构化的步骤列表 JSON，再解析为 {@link PlanStep} 列表。
     *
     * @param task 用户的自然语言任务描述
     * @return 有序的执行步骤列表
     */
    public List<PlanStep> plan(String task) {
        log.info("[Planner] 开始规划任务: {}", task);

        String systemPrompt = buildPlannerPrompt();
        AiMessage response = chatModel.chat(
                List.of(SystemMessage.from(systemPrompt), UserMessage.from(task))
        ).aiMessage();

        String llmOutput = response.text();
        log.info("[Planner] LLM 输出:\n{}", llmOutput);

        List<PlanStep> steps = parsePlan(llmOutput);
        log.info("[Planner] 规划完成，共 {} 步", steps.size());
        return steps;
    }

    // ==================== 私有方法 ====================

    /**
     * 构建 Planner 的系统提示词。
     *
     * <p>告诉 LLM 它的角色是"任务规划师"，输出格式必须是 JSON 数组。
     */
    private String buildPlannerPrompt() {
        // 收集所有工具描述
        List<McpToolInfo> tools = toolRegistry.listTools();
        StringBuilder toolDesc = new StringBuilder();
        for (McpToolInfo t : tools) {
            try {
                toolDesc.append(String.format(
                        "\n- 工具: %s\n  描述: %s\n  参数: %s\n",
                        t.getName(), t.getDescription(),
                        objectMapper.writeValueAsString(t.getInputSchema())
                ));
            } catch (JsonProcessingException e) {
                log.warn("[Planner] 工具 {} schema 序列化失败", t.getName());
            }
        }

        return """
                你是一个**任务规划师 (Planner)**。你的职责是将用户的复杂任务拆解为多个可执行的步骤。

                ## 可用工具
                %s

                ## 输出格式
                请严格输出一个 JSON 数组，每个元素代表一个步骤，格式如下：
                ```json
                [
                  {
                    "stepId": 1,
                    "tool": "工具名称",
                    "arguments": { 参数键值对 },
                    "purpose": "这一步的目的"
                  }
                ]
                ```

                ## 规划规则
                1. 每一步只调用一个工具。
                2. 步骤数量在 1-5 步之间，不要拆得过细，也不要遗漏关键信息收集。
                3. 工具名称必须是上面列出的某一个。
                4. arguments 必须符合工具的参数 Schema。
                5. 仔细分析任务，考虑是否需要从不同维度/角度收集信息。
                6. 不要输出 JSON 以外的任何内容。
                """.formatted(toolDesc.toString());
    }

    /**
     * 从 LLM 输出中解析计划步骤。
     *
     * <p>支持三种格式：
     * <ul>
     *   <li>纯 JSON 数组 {@code [...]}</li>
     *   <li>Markdown 代码块 {@code ```json [...] ```}</li>
     *   <li>嵌套在对象中 {@code {"steps": [...]}}</li>
     * </ul>
     */
    private List<PlanStep> parsePlan(String llmOutput) {
        try {
            String json = extractJsonArray(llmOutput);
            if (json == null) {
                log.warn("[Planner] 无法从 LLM 输出中提取 JSON 数组，使用回退策略");
                return Collections.emptyList();
            }
            List<PlanStep> steps = objectMapper.readValue(json, new TypeReference<>() {});
            // 校验工具名称有效性
            steps.removeIf(step -> {
                if (toolRegistry.findTool(step.getTool()).isEmpty()) {
                    log.warn("[Planner] 跳过不存在的工具: {}", step.getTool());
                    return true;
                }
                return false;
            });
            // 确保 stepId 连续
            for (int i = 0; i < steps.size(); i++) {
                steps.get(i).setStepId(i + 1);
            }
            return steps;
        } catch (Exception e) {
            log.error("[Planner] 解析计划失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 从文本中提取 JSON 数组。
     */
    private String extractJsonArray(String text) {
        if (text == null) return null;

        // 尝试 ```json ... ```
        int mdStart = text.indexOf("```json");
        if (mdStart >= 0) {
            int contentStart = text.indexOf('\n', mdStart) + 1;
            int contentEnd = text.indexOf("```", contentStart);
            if (contentEnd > contentStart) {
                return text.substring(contentStart, contentEnd).trim();
            }
        }
        // 尝试 ``` ... ```
        int codeStart = text.indexOf("```");
        if (codeStart >= 0) {
            int contentStart = text.indexOf('\n', codeStart) + 1;
            int contentEnd = text.indexOf("```", contentStart);
            if (contentEnd > contentStart) {
                String content = text.substring(contentStart, contentEnd).trim();
                if (content.startsWith("[") || content.startsWith("{")) return content;
            }
        }
        // 尝试直接提取 [...]
        int bracketStart = text.indexOf('[');
        int bracketEnd = text.lastIndexOf(']');
        if (bracketStart >= 0 && bracketEnd > bracketStart) {
            return text.substring(bracketStart, bracketEnd + 1);
        }
        // 尝试提取 {...} 中的 steps 字段
        int braceStart = text.indexOf('{');
        int braceEnd = text.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            try {
                String obj = text.substring(braceStart, braceEnd + 1);
                JsonNode node = objectMapper.readTree(obj);
                if (node.has("steps")) {
                    return objectMapper.writeValueAsString(node.get("steps"));
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
