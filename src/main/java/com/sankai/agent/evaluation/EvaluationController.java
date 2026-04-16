package com.sankai.agent.evaluation;

import com.sankai.agent.evaluation.model.EvalReport;
import org.springframework.web.bind.annotation.*;

/**
 * 评测 REST API 控制器。
 *
 * <h3>接口：</h3>
 * <pre>{@code
 * POST /v1/eval/run   — 执行全部回归测试用例，返回评测报告
 * }</pre>
 */
@RestController
@RequestMapping("/v1/eval")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /**
     * 执行离线评测并返回报告。
     *
     * <p>调用示例：
     * <pre>{@code
     * curl -X POST http://localhost:8080/v1/eval/run
     * }</pre>
     */
    @PostMapping("/run")
    public EvalReport runEvaluation() {
        return evaluationService.runAll();
    }
}
