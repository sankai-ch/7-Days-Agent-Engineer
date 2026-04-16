package com.sankai.agent.orchestration.role;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sankai.agent.orchestration.model.PlanStep;
import com.sankai.agent.orchestration.model.ReviewVerdict;
import com.sankai.agent.orchestration.model.StepResult;
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
 * Reviewer（复核者）角色 —— 编排流水线第三阶段。
 *
 * <p>对 Executor 的执行结果进行<b>质量复核和完整性检查</b>，
 * 判断收集到的信息是否足够回答用户问题，或是否需要补充执行。
 *
 * <h3>核心职责：</h3>
 * <ol>
 *   <li>检查每一步执行结果的有效性（是否成功、数据是否为空）</li>
 *   <li>评估信息的完整性（是否覆盖了用户问题的所有方面）</li>
 *   <li>给出质量评分（0-100）</li>
 *   <li>如果信息不足，提出补充执行的建议</li>
 * </ol>
 *
 * <h3>裁决类型：</h3>
 * <ul>
 *   <li><b>PASS</b> — 信息充分，可直接汇总</li>
 *   <li><b>NEEDS_SUPPLEMENT</b> — 需要补充，返回额外步骤</li>
 *   <li><b>FAIL</b> — 执行结果不可用（极端情况）</li>
 * </ul>
 *
 * <h3>工作流：</h3>
 * <pre>{@code
 * 用户原始任务 + [Step1结果, Step2结果, Step3结果]
 *     │
 *     ▼
 * [Reviewer LLM 分析]
 *     │
 *     ├── PASS (score=85) → 进入汇总阶段
 *     ├── NEEDS_SUPPLEMENT → 返回补充步骤给 Executor
 *     └── FAIL → 返回错误
 * }</pre>
 */
@Service
public class ReviewerService {

    private static final Logger log = LoggerFactory.getLogger(ReviewerService.class);

    private final QwenChatModel chatModel;
    private final ObjectMapper objectMapper;

    public ReviewerService(QwenChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    /**
     * 对执行结果进行复核。
     *
     * @param originalTask 用户原始任务描述
     * @param plan         Planner 的执行计划
     * @param results      Executor 的执行结果
     * @return 复核裁决
     */
    public ReviewVerdict review(String originalTask, List<PlanStep> plan, List<StepResult> results) {
        log.info("[Reviewer] 开始复核，原始任务: {}", originalTask);

        // 快速失败检查：如果所有步骤都失败了，直接判 FAIL
        long failCount = results.stream().filter(r -> !r.isSuccess()).count();
        if (failCount == results.size() && !results.isEmpty()) {
            log.warn("[Reviewer] 所有步骤均失败，直接判定 FAIL");
            return ReviewVerdict.fail("所有执行步骤均失败，无可用数据");
        }

        try {
            // 用 LLM 进行深度复核
            String systemPrompt = buildReviewerPrompt();
            String userInput = buildReviewInput(originalTask, plan, results);

            AiMessage response = chatModel.chat(
                    List.of(SystemMessage.from(systemPrompt), UserMessage.from(userInput))
            ).aiMessage();

            String llmOutput = response.text();
            log.info("[Reviewer] LLM 输出:\n{}", llmOutput);

            return parseVerdict(llmOutput);

        } catch (Exception e) {
            log.error("[Reviewer] 复核异常: {}", e.getMessage(), e);
            // 复核异常时退化为基于规则的简单判断
            return fallbackReview(results);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 构建 Reviewer 的系统提示词。
     */
    private String buildReviewerPrompt() {
        return """
                你是一个**质量复核专家 (Reviewer)**。你的职责是检查工具调用的执行结果，
                判断收集到的信息是否足够完整且正确，以回答用户的原始问题。

                ## 输出格式
                请严格按以下 JSON 格式输出（不要输出其他内容）：
                ```json
                {
                  "verdict": "PASS 或 NEEDS_SUPPLEMENT 或 FAIL",
                  "qualityScore": 0到100的整数,
                  "comment": "你的评价说明",
                  "supplementSteps": []
                }
                ```

                ## 评分标准
                - 90-100: 信息充分，覆盖全面，数据准确
                - 70-89:  信息基本充分，可以给出有价值的回答
                - 50-69:  信息有缺失，建议补充
                - 0-49:   信息严重不足或全部失败

                ## 裁决规则
                1. **PASS**: qualityScore >= 70，信息足够回答用户问题
                2. **NEEDS_SUPPLEMENT**: qualityScore 在 50-69，需要额外调用工具补充
                   - 必须在 supplementSteps 中列出需要补充的步骤
                   - 格式同执行计划步骤: {"stepId":N, "tool":"...", "arguments":{...}, "purpose":"..."}
                3. **FAIL**: qualityScore < 50，执行结果不可用

                ## 注意事项
                - 步骤执行失败（success=false）会拉低评分
                - 工具返回结果为空（total=0）也应酌情扣分
                - supplementSteps 最多 2 步，不要过度补充
                """;
    }

    /**
     * 构建发给 Reviewer LLM 的输入上下文。
     */
    private String buildReviewInput(String originalTask, List<PlanStep> plan,
                                    List<StepResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 用户原始任务\n").append(originalTask).append("\n\n");

        sb.append("## 执行计划（共 ").append(plan.size()).append(" 步）\n");
        for (PlanStep step : plan) {
            sb.append(String.format("- Step %d: [%s] %s\n",
                    step.getStepId(), step.getTool(), step.getPurpose()));
        }

        sb.append("\n## 执行结果\n");
        for (StepResult r : results) {
            sb.append(String.format("### Step %d: %s — %s\n",
                    r.getStepId(), r.getTool(), r.isSuccess() ? "✅ 成功" : "❌ 失败"));
            if (r.isSuccess()) {
                try {
                    String resultStr = objectMapper.writeValueAsString(r.getResult());
                    // 截断过长内容避免超出 token 限制
                    if (resultStr.length() > 1500) {
                        resultStr = resultStr.substring(0, 1500) + "...(已截断)";
                    }
                    sb.append(resultStr).append("\n");
                } catch (Exception e) {
                    sb.append("(结果序列化失败)\n");
                }
            } else {
                sb.append("错误: ").append(r.getErrorMessage()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 从 LLM 输出中解析复核裁决。
     */
    private ReviewVerdict parseVerdict(String llmOutput) {
        try {
            String json = extractJson(llmOutput);
            if (json == null) {
                log.warn("[Reviewer] 无法提取 JSON，使用默认 PASS");
                return ReviewVerdict.pass("LLM 输出格式异常，默认通过", 70);
            }

            JsonNode root = objectMapper.readTree(json);

            String verdictStr = root.has("verdict") ? root.get("verdict").asText() : "PASS";
            int score = root.has("qualityScore") ? root.get("qualityScore").asInt() : 70;
            String comment = root.has("comment") ? root.get("comment").asText() : "";

            return switch (verdictStr.toUpperCase()) {
                case "NEEDS_SUPPLEMENT" -> {
                    List<PlanStep> supplementSteps = Collections.emptyList();
                    if (root.has("supplementSteps") && root.get("supplementSteps").isArray()) {
                        supplementSteps = objectMapper.convertValue(
                                root.get("supplementSteps"),
                                objectMapper.getTypeFactory().constructCollectionType(List.class, PlanStep.class)
                        );
                    }
                    yield ReviewVerdict.needsSupplement(comment, score, supplementSteps);
                }
                case "FAIL" -> ReviewVerdict.fail(comment);
                default -> ReviewVerdict.pass(comment, score);
            };
        } catch (Exception e) {
            log.warn("[Reviewer] 解析裁决失败: {}", e.getMessage());
            return ReviewVerdict.pass("解析异常，默认通过", 65);
        }
    }

    /**
     * 当 LLM 复核异常时的回退策略：基于规则做简单判断。
     */
    private ReviewVerdict fallbackReview(List<StepResult> results) {
        long successCount = results.stream().filter(StepResult::isSuccess).count();
        double successRate = results.isEmpty() ? 0 : (double) successCount / results.size();

        if (successRate >= 0.7) {
            return ReviewVerdict.pass(
                    String.format("回退判定: %d/%d 步骤成功", successCount, results.size()),
                    (int) (successRate * 100));
        } else if (successRate >= 0.3) {
            return ReviewVerdict.needsSupplement(
                    String.format("回退判定: 成功率 %.0f%% 偏低", successRate * 100),
                    (int) (successRate * 100), Collections.emptyList());
        } else {
            return ReviewVerdict.fail(
                    String.format("回退判定: 成功率 %.0f%% 过低", successRate * 100));
        }
    }

    /**
     * 从文本中提取 JSON 对象。
     */
    private String extractJson(String text) {
        if (text == null) return null;
        // ```json ... ```
        int mdStart = text.indexOf("```json");
        if (mdStart >= 0) {
            int start = text.indexOf('\n', mdStart) + 1;
            int end = text.indexOf("```", start);
            if (end > start) return text.substring(start, end).trim();
        }
        // ``` ... ```
        int codeStart = text.indexOf("```");
        if (codeStart >= 0) {
            int start = text.indexOf('\n', codeStart) + 1;
            int end = text.indexOf("```", start);
            if (end > start) {
                String c = text.substring(start, end).trim();
                if (c.startsWith("{")) return c;
            }
        }
        // 直接 {...}
        int s = text.indexOf('{');
        int e = text.lastIndexOf('}');
        if (s >= 0 && e > s) return text.substring(s, e + 1);
        return null;
    }
}
