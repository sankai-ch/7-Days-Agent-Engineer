package com.sankai.agent.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sankai.agent.agent.AgentService;
import com.sankai.agent.agent.model.AgentResponse;
import com.sankai.agent.evaluation.model.EvalCase;
import com.sankai.agent.evaluation.model.EvalReport;
import com.sankai.agent.evaluation.model.EvalResult;
import com.sankai.agent.observability.CostTracker;
import com.sankai.agent.orchestration.OrchestrationService;
import com.sankai.agent.orchestration.model.OrchestrationResponse;
import com.sankai.agent.orchestration.model.StepResult;
import dev.langchain4j.internal.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 离线评测服务 —— Day 5 核心组件。
 *
 * <p>加载回归测试集 JSON，批量执行评测用例，
 * 检查工具调用是否符合期望、回答是否包含关键词，
 * 生成包含成功率/时延/成本/失败原因的完整评测报告。
 *
 * <h3>评测流程：</h3>
 * <pre>{@code
 * 1. 加载 eval/regression-test-cases.json
 * 2. 逐条执行（agent 或 orchestrate）
 * 3. 检查：
 *    - 是否成功返回
 *    - 是否调用了期望的工具
 *    - 回答是否包含期望关键词
 * 4. 汇总成 EvalReport
 * }</pre>
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final AgentService agentService;
    private final OrchestrationService orchestrationService;
    private final CostTracker costTracker;
    private final ObjectMapper objectMapper;

    public EvaluationService(AgentService agentService,
                              OrchestrationService orchestrationService,
                              CostTracker costTracker,
                              ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.orchestrationService = orchestrationService;
        this.costTracker = costTracker;
        this.objectMapper = objectMapper;
    }

    /**
     * 加载并执行全部回归测试用例。
     *
     * @return 评测报告
     */
    public EvalReport runAll() {
        log.info("========== [评测] 开始离线评测 ==========");
        List<EvalCase> cases = loadTestCases();
        log.info("[评测] 加载了 {} 条用例", cases.size());

        List<EvalResult> results = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            EvalCase tc = cases.get(i);
            log.info("[评测] ({}/{}) 执行用例 {}: {}", i + 1, cases.size(), tc.getId(), tc.getInput());
            EvalResult result = evaluateOne(tc);
            results.add(result);
            log.info("[评测] ({}/{}) 用例 {} → {}",
                    i + 1, cases.size(), tc.getId(), result.isPassed() ? "✅ PASS" : "❌ FAIL");
        }

        EvalReport report = EvalReport.fromResults(results);
        log.info("========== [评测] 评测完成 ==========");
        log.info("[评测] 总计: {}, 通过: {}, 失败: {}, 通过率: {}",
                report.getTotalCases(), report.getPassedCases(),
                report.getFailedCases(), report.getPassRate());
        log.info("[评测] 平均耗时: {}ms, Token: {}, 成本: {}",
                report.getAvgDurationMs(), report.getTotalTokenEstimate(), report.getTotalCostEstimate());
        return report;
    }

    /**
     * 执行单条评测用例。
     */
    private EvalResult evaluateOne(EvalCase tc) {
        EvalResult result = new EvalResult();
        result.setCaseId(tc.getId());
        result.setInput(tc.getInput());

        long start = System.currentTimeMillis();
        List<String> checks = new ArrayList<>();
        boolean allPassed = true;

        try {
            // 根据 mode 选择执行方式
            String answer;
            List<String> actualTools = new ArrayList<>();

            if ("orchestrate".equals(tc.getMode())) {
                OrchestrationResponse resp = orchestrationService.orchestrate(tc.getInput());
                answer = resp.getAnswer();
                if (resp.getStepResults() != null) {
                    resp.getStepResults().stream()
                            .filter(StepResult::isSuccess)
                            .forEach(sr -> actualTools.add(sr.getTool()));
                }
                if (!resp.isSuccess()) {
                    allPassed = false;
                    checks.add("❌ 编排执行失败: " + resp.getErrorMessage());
                }
            } else {
                AgentResponse resp = agentService.chat(tc.getInput());
                answer = resp.getAnswer();
                if (resp.getToolCalls() != null) {
                    resp.getToolCalls().stream()
                            .filter(AgentResponse.ToolCallRecord::isSuccess)
                            .forEach(tc2 -> actualTools.add(tc2.getTool()));
                }
                if (!resp.isSuccess()) {
                    allPassed = false;
                    checks.add("❌ Agent 执行失败: " + resp.getErrorMessage());
                }
            }

            result.setActualOutput(answer);
            result.setActualToolCalls(actualTools);

            // 检查 1: 工具调用是否符合期望
            if (tc.getExpectedToolCalls() != null && !tc.getExpectedToolCalls().isEmpty()) {
                for (String expected : tc.getExpectedToolCalls()) {
                    if (actualTools.contains(expected)) {
                        checks.add("✅ 工具 " + expected + " 已调用");
                    } else {
                        checks.add("❌ 期望调用工具 " + expected + " 但未调用");
                        allPassed = false;
                    }
                }
            }

            // 检查 2: 回答是否包含关键词
            if (tc.getExpectedKeywords() != null && !tc.getExpectedKeywords().isEmpty() && answer != null) {
                for (String keyword : tc.getExpectedKeywords()) {
                    if (answer.contains(keyword)) {
                        checks.add("✅ 回答包含关键词: " + keyword);
                    } else {
                        checks.add("⚠️ 回答未包含关键词: " + keyword + "（不影响通过）");
                        // 关键词缺失不直接判 fail，作为参考
                    }
                }
            }

            // Token/Cost 估算
            double[] est = costTracker.estimate(tc.getInput(), answer != null ? answer : "");
            result.setTokenEstimate((int) est[0]);
            result.setCostEstimate(est[1]);

        } catch (Exception e) {
            allPassed = false;
            checks.add("❌ 执行异常: " + e.getMessage());
            result.setErrorMessage(e.getMessage());
        }

        result.setDurationMs(System.currentTimeMillis() - start);
        result.setCheckDetails(checks);
        result.setPassed(allPassed);
        return result;
    }

    /**
     * 从 classpath 加载回归测试用例集。
     */
    private List<EvalCase> loadTestCases() {
        try {
            InputStream is = new ClassPathResource("eval/regression-test-cases.json").getInputStream();
            return objectMapper.readValue(is, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("[评测] 加载测试用例失败: {}", e.getMessage());
            return List.of();
        }
    }
}
