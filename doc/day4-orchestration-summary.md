---

# Day 4: Agent 编排 — 设计与实现总结

## 1. 文档信息
*   **版本**：v1.0
*   **目标**：实现 Planner / Executor / Reviewer 三角色协作流水线，复杂任务质量高于单 Agent 基线。
*   **核心组件**：OrchestrationService + PlannerService + ExecutorService + ReviewerService。
*   **验收标准**：对同一批任务，编排模式的完成质量（多工具覆盖、质量评分、容错能力）高于 Day 3 单 Agent。

## 2. 核心升级：Day 3 单 Agent → Day 4 三角色编排

| 维度 | Day 3 单 Agent (`/v1/agent/chat`) | Day 4 三角色编排 (`/v1/orchestrate`) |
|------|-----------------------------------|--------------------------------------|
| 工具调用 | 单工具单次 | 多工具多步，自动拆解 |
| 质量控制 | 无 | Reviewer 评分 + 裁决 + 补充循环 |
| 复杂任务 | 能力有限 | Planner 自动分解为 1-5 步 |
| 容错 | 单步失败即终止 | 单步失败继续执行 + 补充 |
| 可观测性 | 单层 | 全链路（计划 / 执行 / 复核 / 汇总） |

## 3. 架构设计

### 3.1 四阶段流水线

```
┌────────────────────────────────────────────────────────────────────┐
│                     OrchestrationService                           │
│                                                                    │
│   用户任务                                                         │
│      │                                                             │
│      ▼                                                             │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐     │
│  │ Planner  │───>│ Executor │───>│ Reviewer │───>│Summarizer│     │
│  │ (规划)   │    │ (执行)   │    │ (复核)   │    │ (汇总)   │     │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘     │
│   LLM 拆解任务    逐步调用工具     LLM 评分裁决    LLM 综合回答    │
│   → Plan[1..N]   → Results[1..N]  → Verdict       → Answer        │
│                                       │                            │
│                                  ┌────┴─────┐                     │
│                                  │ 需要补充？ │                     │
│                                  └────┬─────┘                     │
│                                       │ YES                       │
│                                  Executor(补充步骤)                │
│                                       │                            │
│                                  Reviewer(重新复核)                │
└────────────────────────────────────────────────────────────────────┘
```

### 3.2 三角色职责分离

```
┌─────────────────────────────────────────────────────┐
│  Planner (规划者)                                    │
│  - 理解用户意图                                      │
│  - 将复杂任务拆解为 1~5 个原子步骤                    │
│  - 为每步选择工具和参数                               │
│  - 输出: List<PlanStep>                              │
├─────────────────────────────────────────────────────┤
│  Executor (执行者)                                   │
│  - 按步骤顺序调用 MCP 工具                            │
│  - 记录每步入参、出参、耗时、成功/失败                 │
│  - 单步失败不中断，继续后续步骤                        │
│  - 输出: List<StepResult>                            │
├─────────────────────────────────────────────────────┤
│  Reviewer (复核者)                                   │
│  - 检查执行结果的有效性和完整性                        │
│  - 给出质量评分 (0-100)                              │
│  - 三种裁决: PASS / NEEDS_SUPPLEMENT / FAIL          │
│  - 如需补充，返回额外步骤给 Executor 二次执行          │
│  - 输出: ReviewVerdict                               │
└─────────────────────────────────────────────────────┘
```

### 3.3 包结构

```
com.sankai.agent.orchestration
├── OrchestrationService.java         # 编排调度器（串联四阶段）
├── OrchestrationController.java      # REST API
├── model/
│   ├── PlanStep.java                 # 计划步骤
│   ├── StepResult.java               # 步骤执行结果
│   ├── ReviewVerdict.java            # 复核裁决
│   └── OrchestrationResponse.java    # 编排完整响应
└── role/
    ├── PlannerService.java           # Planner 角色
    ├── ExecutorService.java          # Executor 角色
    └── ReviewerService.java          # Reviewer 角色
```

## 4. 调用方式

### 4.1 编排接口（Day 4 新增）

```bash
# 三角色编排模式 —— 适合复杂分析任务
curl -X POST http://localhost:8080/v1/orchestrate \
  -H "Content-Type: application/json" \
  -d '{"query": "分析系统当前的健康状况，包括错误日志和未解决的高优工单"}'
```

### 4.2 响应结构

```json
{
  "answer": "综合分析结果如下：\n1. 当前有3条ERROR级别日志...\n2. 有2个高优先级未解决工单...",
  "plan": [
    {"stepId": 1, "tool": "query_logs",    "arguments": {"level": "ERROR"},        "purpose": "查询错误日志"},
    {"stepId": 2, "tool": "query_tickets", "arguments": {"status": "OPEN", "priority": "HIGH"}, "purpose": "查询高优工单"},
    {"stepId": 3, "tool": "query_tickets", "arguments": {"status": "OPEN", "priority": "CRITICAL"}, "purpose": "查询紧急工单"}
  ],
  "stepResults": [
    {"stepId": 1, "tool": "query_logs",    "success": true, "durationMs": 85,  "result": {...}},
    {"stepId": 2, "tool": "query_tickets", "success": true, "durationMs": 120, "result": {...}},
    {"stepId": 3, "tool": "query_tickets", "success": true, "durationMs": 95,  "result": {...}}
  ],
  "review": {
    "verdict": "PASS",
    "qualityScore": 88,
    "comment": "三个步骤均成功，覆盖了错误日志和高优/紧急工单两个维度"
  },
  "supplementResults": [],
  "totalDurationMs": 3200,
  "success": true,
  "mode": "planner-executor-reviewer"
}
```

### 4.3 单 Agent 接口（Day 3 原有，仍可用）

```bash
# 单 Agent 模式 —— 适合简单查询
curl -X POST http://localhost:8080/v1/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"query": "最近有什么 ERROR 日志？"}'
```

### 4.4 两种模式对比验证

| 测试任务 | 单 Agent (Day3) | 三角色编排 (Day4) |
|----------|-----------------|-------------------|
| "查一下 ERROR 日志" | ✅ 1 步完成 | ✅ 1 步完成（简单任务自动退化） |
| "分析系统健康状况" | ⚠️ 只调 1 个工具 | ✅ 自动拆解为 2-3 步，覆盖多维度 |
| "张三负责的工单情况及相关日志" | ⚠️ 只查工单或日志 | ✅ 先查工单再查日志，交叉分析 |
| 某步骤执行失败 | ❌ 整体失败 | ✅ 跳过继续 + Reviewer 判断是否补充 |

## 5. 配置说明

```yaml
# application.yml 新增
orchestration:
  max-supplement-rounds: 1   # Reviewer 要求补充时最多允许几轮
```

## 6. 测试覆盖

| 测试用例 | 验证内容 | 状态 |
|----------|----------|------|
| `testPlanStep` | PlanStep 模型正确构建 | ✅ |
| `testStepResult` | StepResult.ok / .fail 工厂方法 | ✅ |
| `testReviewVerdict` | PASS / NEEDS_SUPPLEMENT / FAIL 三种裁决 | ✅ |
| `testOrchestrationResponse` | success / failure 响应构建 | ✅ |
| `testExecutorSuccess` | Executor 正常执行多步计划 | ✅ |
| `testExecutorPartialFailure` | Executor 单步失败不中断后续步骤 | ✅ |
| `testReviewerAllFail` | Reviewer 全部失败直接判 FAIL | ✅ |

## 7. 新增文件清单

| 文件 | 功能 |
|------|------|
| `orchestration/model/PlanStep.java` | 计划步骤模型 |
| `orchestration/model/StepResult.java` | 步骤执行结果模型 |
| `orchestration/model/ReviewVerdict.java` | 复核裁决模型 |
| `orchestration/model/OrchestrationResponse.java` | 编排完整响应模型 |
| `orchestration/role/PlannerService.java` | Planner 规划角色 |
| `orchestration/role/ExecutorService.java` | Executor 执行角色 |
| `orchestration/role/ReviewerService.java` | Reviewer 复核角色 |
| `orchestration/OrchestrationService.java` | 编排调度器 |
| `orchestration/OrchestrationController.java` | REST API 控制器 |
| `OrchestrationTest.java` | 7 个测试用例 |

## 8. 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 角色间通信 | 内存直调 | 同进程内三个 Service，无需引入消息队列 |
| Executor 容错 | 单步失败继续 | 不因部分失败丢失已有结果 |
| Reviewer 实现 | LLM + 规则回退 | LLM 异常时退化为成功率简单计算 |
| 补充循环上限 | 可配置（默认 1 轮） | 防止无限补充消耗 token |
| Planner 步骤上限 | 提示词约束 1-5 步 | 平衡粒度与 token 成本 |

## 9. 与 Day 1-3 的衔接

```
Day 1                Day 2              Day 3              Day 4
结构化输出            RAG 知识库          MCP 工具           三角色编排
─────────           ─────────          ─────────          ─────────
ExtractController   AskController      AgentController    OrchestrationController
      │                  │                   │                   │
ExtractionService   RagService         AgentService       OrchestrationService
      │                  │                   │              ┌────┼────┐
   LlmClient       RetrievalService   McpToolRegistry    Planner Executor Reviewer
                         │              ┌───┼───┐              │     │
                    KnowledgeBase     query_ query_ query_     │     │
                         │            database logs  tickets   │     │
                         │                 │                   │     │
                    ───── PostgreSQL (pgvector) ────────────────┘     │
                    knowledge_segments | app_logs | work_tickets      │
                                                                      │
                    Day 4 Executor 复用 Day 3 的 McpToolRegistry ──────┘
```
