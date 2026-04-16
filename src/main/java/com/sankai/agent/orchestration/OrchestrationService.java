package com.sankai.agent.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sankai.agent.orchestration.model.*;
import com.sankai.agent.orchestration.role.ExecutorService;
import com.sankai.agent.orchestration.role.PlannerService;
import com.sankai.agent.orchestration.role.ReviewerService;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 编排调度器 —— Day 4 核心组件。
 *
 * <p>协调 Planner → Executor → Reviewer → 汇总 四阶段流水线，
 * 将复杂任务拆解执行并质量把关后生成最终回答。
 *
 * <h3>四阶段流水线：</h3>
 * <pre>{@code
 * ┌──────────────────────────────────────────────────────────────────┐
 * │                    OrchestrationService                         │
 * │                                                                  │
 * │  ① Planner ──> ② Executor ──> ③ Reviewer ──> ④ Summarizer       │
 * │     │              │              │              │               │
 * │  拆解任务       逐步执行        质量复核       汇总回答          │
 * │  生成计划       调用工具        评分裁决       自然语言          │
 * │     │              │              │              │               │
 * │  Plan[1..N]    Results[1..N]   Verdict       Final Answer       │
 * │                                   │                              │
 * │                            ┌──────┴──────┐                      │
 * │                            │  补充循环?   │                      │
 * │                            │ NEEDS_SUPP   │                      │
 * │                            └──────┬──────┘                      │
 * │                                   │                              │
 * │                            Executor (补充步骤)                   │
 * └──────────────────────────────────────────────────────────────────┘
 * }</pre>
 *
 * <h3>对比 Day 3 单 Agent 模式的优势：</h3>
 * <table>
 *   <tr><th>维度</th><th>Day 3 单 Agent</th><th>Day 4 三角色编排</th></tr>
 *   <tr><td>工具调用</td><td>单工具</td><td>多工具多步</td></tr>
 *   <tr><td>质量控制</td><td>无</td><td>Reviewer 评分+裁决</td></tr>
 *   <tr><td>复杂任务</td><td>能力有限</td><td>自动拆解+编排</td></tr>
 *   <tr><td>容错</td><td>单步失败即终止</td><td>单步失败继续+补充</td></tr>
 *   <tr><td>可观测性</td><td>单层</td><td>全链路(计划/执行/复核/汇总)</td></tr>
 * </table>
 */
@Service
public class OrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationService.class);

    private final PlannerService planner;
    private final ExecutorService executor;
    private final ReviewerService reviewer;
    private final QwenChatModel chatModel;
    private final ObjectMapper objectMapper;

    /** Reviewer 判定 NEEDS_SUPPLEMENT 时最多允许补充几轮 */
    @Value("${orchestration.max-supplement-rounds:1}")
    private int maxSupplementRounds;

    public OrchestrationService(PlannerService planner,
                                 ExecutorService executor,
                                 ReviewerService reviewer,
                                 QwenChatModel chatModel,
                                 ObjectMapper objectMapper) {
        this.planner = planner;
        this.executor = executor;
        this.reviewer = reviewer;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行完整的编排流水线。
     *
     * <p>流程：
     * <ol>
     *   <li><b>Plan</b> — Planner 将任务拆解为多步计划</li>
     *   <li><b>Execute</b> — Executor 按计划逐步执行工具</li>
     *   <li><b>Review</b> — Reviewer 复核执行结果质量</li>
     *   <li><b>Supplement</b> — (可选) 若 Reviewer 要求补充，执行补充步骤后重新复核</li>
     *   <li><b>Summarize</b> — 将所有结果汇总为自然语言回答</li>
     * </ol>
     *
     * @param task 用户的自然语言任务
     * @return 包含全链路信息的编排响应
     */
    public OrchestrationResponse orchestrate(String task) {
        long globalStart = System.currentTimeMillis();
        log.info("========== [编排] 开始处理任务 ==========");
        log.info("[编排] 用户任务: {}", task);

        try {
            // ==================== 阶段 1: 规划 ====================
            log.info("[编排] ▶ 阶段 1/4: Planner 规划中...");
            List<PlanStep> plan = planner.plan(task);

            if (plan.isEmpty()) {
                log.warn("[编排] Planner 未生成任何步骤，回退到直接回答");
                return fallbackDirectAnswer(task, globalStart);
            }
            log.info("[编排] ✓ 规划完成，共 {} 步", plan.size());

            // ==================== 阶段 2: 执行 ====================
            log.info("[编排] ▶ 阶段 2/4: Executor 执行中...");
            List<StepResult> stepResults = executor.executeAll(plan);
            log.info("[编排] ✓ 执行完成");

            // ==================== 阶段 3: 复核 ====================
            log.info("[编排] ▶ 阶段 3/4: Reviewer 复核中...");
            ReviewVerdict verdict = reviewer.review(task, plan, stepResults);
            log.info("[编排] ✓ 复核完成: verdict={}, score={}", verdict.getVerdict(), verdict.getQualityScore());

            // ==================== 阶段 3.5: 补充循环 ====================
            List<StepResult> supplementResults = new ArrayList<>();
            int supplementRound = 0;

            while (verdict.needsSupplement()
                    && supplementRound < maxSupplementRounds
                    && verdict.getSupplementSteps() != null
                    && !verdict.getSupplementSteps().isEmpty()) {

                supplementRound++;
                log.info("[编排] ▶ 补充执行第 {} 轮，共 {} 步",
                        supplementRound, verdict.getSupplementSteps().size());

                // 执行补充步骤
                List<StepResult> suppResults = executor.executeAll(verdict.getSupplementSteps());
                supplementResults.addAll(suppResults);

                // 合并所有结果重新复核
                List<StepResult> allResults = new ArrayList<>(stepResults);
                allResults.addAll(supplementResults);

                List<PlanStep> allPlan = new ArrayList<>(plan);
                allPlan.addAll(verdict.getSupplementSteps());

                verdict = reviewer.review(task, allPlan, allResults);
                log.info("[编排] ✓ 补充复核: verdict={}, score={}",
                        verdict.getVerdict(), verdict.getQualityScore());
            }

            // ==================== 阶段 4: 汇总 ====================
            log.info("[编排] ▶ 阶段 4/4: 汇总回答...");
            List<StepResult> allResults = new ArrayList<>(stepResults);
            allResults.addAll(supplementResults);

            String answer = summarize(task, plan, allResults, verdict);
            long totalDuration = System.currentTimeMillis() - globalStart;

            log.info("========== [编排] 完成，总耗时 {}ms ==========", totalDuration);

            OrchestrationResponse response = OrchestrationResponse.success(
                    answer, plan, stepResults, verdict, totalDuration);
            response.setSupplementResults(supplementResults);
            return response;

        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - globalStart;
            log.error("[编排] 流水线异常: {}", e.getMessage(), e);
            return OrchestrationResponse.failure(e.getMessage(), totalDuration);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 阶段 4：使用 LLM 将所有执行结果汇总为自然语言回答。
     *
     * <p>将用户任务、执行结果、Reviewer 评价一起发给 LLM，
     * 生成结构化的综合分析回答。
     */
    private String summarize(String task, List<PlanStep> plan,
                              List<StepResult> results, ReviewVerdict verdict) {
        StringBuilder context = new StringBuilder();

        // 拼接所有步骤的结果
        for (StepResult r : results) {
            if (r.isSuccess()) {
                try {
                    String resultStr = objectMapper.writeValueAsString(r.getResult());
                    if (resultStr.length() > 2000) {
                        resultStr = resultStr.substring(0, 2000) + "...(已截断)";
                    }
                    context.append(String.format("\n【步骤 %d — %s】:\n%s\n",
                            r.getStepId(), r.getTool(), resultStr));
                } catch (Exception e) {
                    context.append(String.format("\n【步骤 %d — %s】: (序列化失败)\n",
                            r.getStepId(), r.getTool()));
                }
            } else {
                context.append(String.format("\n【步骤 %d — %s】: 执行失败 — %s\n",
                        r.getStepId(), r.getTool(), r.getErrorMessage()));
            }
        }

        String systemPrompt = """
                你是一个**汇总分析师**。请根据多个工具的执行结果，对用户的问题给出全面、有条理的回答。

                ## 回答规则
                1. 综合所有步骤的数据给出分析结论
                2. 如果是列表数据，使用序号清晰列出
                3. 如果某些步骤失败了，说明哪些信息未能获取
                4. 在回答末尾给出简要总结或建议
                5. 语气专业、简洁
                """;

        String userInput = String.format(
                "## 用户问题\n%s\n\n## Reviewer 评价\n评分: %d/100\n评语: %s\n\n## 工具执行结果\n%s",
                task, verdict.getQualityScore(), verdict.getComment(), context);

        AiMessage response = chatModel.chat(
                List.of(SystemMessage.from(systemPrompt), UserMessage.from(userInput))
        ).aiMessage();

        return response.text();
    }

    /**
     * 回退策略：Planner 无法生成计划时，直接用单轮 LLM 回答。
     */
    private OrchestrationResponse fallbackDirectAnswer(String task, long globalStart) {
        log.info("[编排] 回退: 直接用 LLM 回答");
        AiMessage response = chatModel.chat(
                List.of(SystemMessage.from("你是一个智能运维助手，请直接回答用户问题。"),
                        UserMessage.from(task))
        ).aiMessage();

        long duration = System.currentTimeMillis() - globalStart;
        return OrchestrationResponse.success(
                response.text(), List.of(), List.of(),
                ReviewVerdict.pass("直接回答，无需工具调用", 60), duration);
    }
}
