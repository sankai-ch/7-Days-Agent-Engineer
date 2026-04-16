package com.sankai.agent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sankai.agent.agent.model.AgentResponse;
import com.sankai.agent.agent.model.AgentResponse.ToolCallRecord;
import com.sankai.agent.mcp.protocol.McpToolInfo;
import com.sankai.agent.mcp.server.McpToolRegistry;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Agent 智能编排服务 —— Day 3 核心组件。
 *
 * <p>实现了一个具备<b>工具调用能力</b>的智能 Agent。核心流程：
 * <ol>
 *   <li><b>意图理解</b>：接收用户自然语言查询，发送给 LLM 分析意图</li>
 *   <li><b>工具选择</b>：LLM 根据工具描述决定调用哪个工具及参数</li>
 *   <li><b>工具执行</b>：通过 McpToolRegistry 调用对应工具获取结果</li>
 *   <li><b>结果汇总</b>：将工具返回结果再次发给 LLM，生成最终自然语言回答</li>
 * </ol>
 *
 * <h3>Agent 工作流图：</h3>
 * <pre>{@code
 *  用户问题 ──> [LLM 第1轮: 分析意图 + 选择工具]
 *                         │
 *                ┌────────┼────────┐
 *                ▼        ▼        ▼
 *          query_database query_logs query_tickets
 *                │        │        │
 *                └────────┼────────┘
 *                         ▼
 *              [LLM 第2轮: 汇总结果生成回答]
 *                         │
 *                         ▼
 *                    最终回答 ──> 用户
 * }</pre>
 *
 * <p>设计模式：<b>ReAct (Reasoning + Acting)</b>
 * <p>Agent 先推理（Reasoning）决定行动方案，再执行（Acting）工具调用，
 * 最后根据执行结果生成最终回答。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final QwenChatModel chatModel;
    private final McpToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    /** 最大工具调用轮次，防止无限循环 */
    @Value("${agent.max-tool-rounds:3}")
    private int maxToolRounds;

    public AgentService(QwenChatModel chatModel,
                        McpToolRegistry toolRegistry,
                        ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理用户查询 —— Agent 的主入口方法。
     *
     * <p>完整流程：
     * <ol>
     *   <li>构建系统提示词（包含工具描述）</li>
     *   <li>发送给 LLM 分析，获取工具调用指令</li>
     *   <li>解析 LLM 返回的 JSON，提取工具名和参数</li>
     *   <li>调用工具获取结果</li>
     *   <li>将结果反馈给 LLM 生成最终回答</li>
     * </ol>
     *
     * @param query 用户的自然语言查询
     * @return Agent 的完整响应（含工具调用链和最终回答）
     */
    public AgentResponse chat(String query) {
        log.info("[Agent] 收到用户查询: {}", query);

        List<ToolCallRecord> allToolCalls = new ArrayList<>();
        String thinking = "";

        try {
            // ===== 第 1 步：构建提示词并让 LLM 决策 =====
            String systemPrompt = buildSystemPrompt();
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(systemPrompt));
            messages.add(UserMessage.from(query));

            AiMessage firstResponse = chatModel.chat(messages).aiMessage();
            String llmOutput = firstResponse.text();
            log.info("[Agent] LLM 第 1 轮返回:\n{}", llmOutput);

            // ===== 第 2 步：解析 LLM 输出，提取工具调用指令 =====
            ToolCallInstruction instruction = parseToolCall(llmOutput);

            if (instruction == null) {
                // LLM 认为不需要调用工具，直接返回回答
                log.info("[Agent] LLM 判断无需调用工具，直接回答");
                return AgentResponse.success(llmOutput, allToolCalls, "无需工具调用，直接回答用户问题");
            }

            thinking = instruction.thinking;

            // ===== 第 3 步：执行工具调用（支持多轮） =====
            StringBuilder toolResultsContext = new StringBuilder();
            int round = 0;

            while (instruction != null && round < maxToolRounds) {
                round++;
                log.info("[Agent] 第 {} 轮工具调用: tool={}, args={}",
                        round, instruction.toolName, instruction.arguments);

                // 调用工具
                long startTime = System.currentTimeMillis();
                Object toolResult;
                boolean callSuccess;
                try {
                    toolResult = toolRegistry.invokeTool(instruction.toolName, instruction.arguments);
                    callSuccess = true;
                } catch (Exception e) {
                    toolResult = Map.of("error", e.getMessage());
                    callSuccess = false;
                    log.warn("[Agent] 工具 {} 调用失败: {}", instruction.toolName, e.getMessage());
                }
                long duration = System.currentTimeMillis() - startTime;

                // 记录工具调用
                allToolCalls.add(new ToolCallRecord(
                        instruction.toolName, instruction.arguments,
                        toolResult, duration, callSuccess));

                // 拼接工具结果
                String resultJson = objectMapper.writeValueAsString(toolResult);
                toolResultsContext.append(String.format(
                        "\n【工具 %s 返回结果】:\n%s\n", instruction.toolName, resultJson));

                // 检查 LLM 是否需要继续调用其他工具（预留多轮扩展）
                instruction = null; // 当前版本只支持单轮工具调用
            }

            // ===== 第 4 步：将工具结果发给 LLM 生成最终回答 =====
            messages.add(AiMessage.from(llmOutput));
            messages.add(UserMessage.from(
                    "以下是工具的返回结果，请根据这些结果用自然语言回答用户的问题。"
                    + "回答要清晰、有条理，如果是列表数据请使用序号排列。\n"
                    + toolResultsContext));

            AiMessage finalResponse = chatModel.chat(messages).aiMessage();
            String finalAnswer = finalResponse.text();
            log.info("[Agent] 最终回答生成完成");

            return AgentResponse.success(finalAnswer, allToolCalls, thinking);

        } catch (Exception e) {
            log.error("[Agent] 处理查询异常: {}", e.getMessage(), e);
            return AgentResponse.failure(e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 构建系统提示词 —— 告诉 LLM 它有哪些工具可用。
     *
     * <p>这是 Agent 的"灵魂"所在：通过精心设计的提示词，让 LLM 理解：
     * <ul>
     *   <li>自己的角色定位</li>
     *   <li>可以使用哪些工具</li>
     *   <li>如何表达工具调用意图（输出格式约束）</li>
     * </ul>
     *
     * @return 系统提示词字符串
     */
    private String buildSystemPrompt() {
        // 获取所有工具描述
        List<McpToolInfo> tools = toolRegistry.listTools();
        StringBuilder toolDescriptions = new StringBuilder();
        for (McpToolInfo tool : tools) {
            try {
                toolDescriptions.append(String.format(
                        "\n工具名称: %s\n描述: %s\n参数 Schema: %s\n",
                        tool.getName(),
                        tool.getDescription(),
                        objectMapper.writeValueAsString(tool.getInputSchema())
                ));
            } catch (JsonProcessingException e) {
                log.warn("[Agent] 工具 {} 的 Schema 序列化失败", tool.getName());
            }
        }

        return """
                你是一个智能运维助手 Agent，可以通过调用工具来回答用户问题。

                ## 可用工具列表
                %s

                ## 工作规则
                1. 分析用户的问题，判断是否需要调用工具。
                2. 如果需要调用工具，请严格按以下 JSON 格式输出（不要输出其他内容）：
                ```json
                {
                  "thinking": "你的分析思考过程",
                  "tool_call": {
                    "name": "工具名称",
                    "arguments": { 参数键值对 }
                  }
                }
                ```
                3. 如果不需要调用工具（例如打招呼、闲聊），直接用自然语言回答即可。
                4. 一次只调用一个工具。
                5. 工具名称必须是上面列表中的某一个，参数必须符合 Schema 定义。
                """.formatted(toolDescriptions.toString());
    }

    /**
     * 解析 LLM 返回的文本，提取工具调用指令。
     *
     * <p>LLM 应返回包含 {@code tool_call} 的 JSON 块。
     * 如果返回的是纯文本（没有工具调用意图），返回 null。
     *
     * @param llmOutput LLM 返回的原始文本
     * @return 解析出的工具调用指令，或 null（无需调用工具）
     */
    @SuppressWarnings("unchecked")
    private ToolCallInstruction parseToolCall(String llmOutput) {
        try {
            // 尝试从 LLM 输出中提取 JSON 块
            String json = extractJson(llmOutput);
            if (json == null) {
                return null; // 没有 JSON，说明是直接回答
            }

            JsonNode root = objectMapper.readTree(json);

            // 检查是否包含 tool_call 字段
            if (!root.has("tool_call")) {
                return null;
            }

            JsonNode toolCall = root.get("tool_call");
            String toolName = toolCall.get("name").asText();
            Map<String, Object> arguments = new HashMap<>();
            if (toolCall.has("arguments")) {
                arguments = objectMapper.convertValue(toolCall.get("arguments"), Map.class);
            }

            String thinking = root.has("thinking") ? root.get("thinking").asText() : "";

            return new ToolCallInstruction(toolName, arguments, thinking);

        } catch (Exception e) {
            log.warn("[Agent] 解析工具调用指令失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 LLM 输出中提取 JSON 代码块。
     *
     * <p>支持两种格式：
     * <ul>
     *   <li>Markdown 代码块：{@code ```json ... ```}</li>
     *   <li>直接 JSON 对象：{@code { ... }}</li>
     * </ul>
     *
     * @param text LLM 输出文本
     * @return 提取的 JSON 字符串，或 null
     */
    private String extractJson(String text) {
        if (text == null) return null;

        // 尝试提取 ```json ... ``` 代码块
        int jsonStart = text.indexOf("```json");
        if (jsonStart >= 0) {
            int contentStart = text.indexOf('\n', jsonStart) + 1;
            int contentEnd = text.indexOf("```", contentStart);
            if (contentEnd > contentStart) {
                return text.substring(contentStart, contentEnd).trim();
            }
        }

        // 尝试提取 ``` ... ``` 代码块
        int codeStart = text.indexOf("```");
        if (codeStart >= 0) {
            int contentStart = text.indexOf('\n', codeStart) + 1;
            int contentEnd = text.indexOf("```", contentStart);
            if (contentEnd > contentStart) {
                String content = text.substring(contentStart, contentEnd).trim();
                if (content.startsWith("{")) return content;
            }
        }

        // 尝试直接匹配 JSON 对象
        int braceStart = text.indexOf('{');
        int braceEnd = text.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return text.substring(braceStart, braceEnd + 1);
        }

        return null;
    }

    /**
     * 工具调用指令 —— 从 LLM 输出中解析出的结构化指令。
     *
     * @param toolName  要调用的工具名称
     * @param arguments 工具参数
     * @param thinking  LLM 的思考过程
     */
    private record ToolCallInstruction(
            String toolName,
            Map<String, Object> arguments,
            String thinking
    ) {}
}
