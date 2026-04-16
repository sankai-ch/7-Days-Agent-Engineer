---

# Day 5: 可观测与评测 — 完成总结

## 1. 做了什么

为整个 Agent 系统加入了**链路追踪（Tracing）**、**Token/Cost 统计**、**可观测性 API** 和**离线评测框架**，
实现"每次运行能看到成功率、时延、成本、失败原因"的验收目标。

### 关键改进：ObservableChatModel 代理层

原始方案中 AOP 切面只拦截了 Service 级别方法，无法捕获内部直接调用的 `chatModel.chat()`（共 7 处）。

**修复方案**：创建 `ObservableChatModel implements ChatModel` 代理层，包裹原始 `QwenChatModel`：
- 每次 `chat()` 调用自动创建 `llm` 类型的 Span
- 精确记录每次 LLM 调用的输入/输出 Token 和成本
- 所有 Service 注入类型从 `QwenChatModel` 改为 `ChatModel` 接口（更好的解耦）

```
修改前（AOP 无法拦截）：
[planner] PlannerService.plan  800ms  ← 只有阶段 Span，看不到 LLM 细节

修改后（ObservableChatModel 自动拦截）：
[planner] PlannerService.plan  800ms
└── [llm] chatModel.chat#1    780ms  tokens=1200, cost=¥0.0018  ← 精确记录
```

### 新增能力一览

```
┌──────────────────────────────────────────────────────────────┐
│                    Day 5 可观测与评测                         │
│                                                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │  链路追踪        │  │  Token/Cost     │  │  离线评测    │ │
│  │  TracingService  │  │  CostTracker    │  │  Evaluation  │ │
│  │  TraceSpan 树    │  │  估算 token     │  │  Service     │ │
│  │  ThreadLocal     │  │  估算成本       │  │  10 条用例   │ │
│  └────────┬────────┘  └────────┬────────┘  └──────┬──────┘ │
│           │                    │                   │         │
│           ▼                    ▼                   ▼         │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              TracingAspect (AOP 切面)                    │ │
│  │  自动拦截 Agent / Orchestration / Tool 调用               │ │
│  │  无侵入织入 Span + Token + 耗时                          │ │
│  └─────────────────────────────────────────────────────────┘ │
│           │                                                   │
│           ▼                                                   │
│  ┌──────────────────────────────────────┐                    │
│  │  ObservabilityController             │                    │
│  │  GET /v1/metrics   — 汇总指标        │                    │
│  │  GET /v1/traces    — 最近 Trace 列表  │                    │
│  │  GET /v1/traces/id — 单条 Trace 详情  │                    │
│  └──────────────────────────────────────┘                    │
│  ┌──────────────────────────────────────┐                    │
│  │  EvaluationController                │                    │
│  │  POST /v1/eval/run — 执行离线评测     │                    │
│  └──────────────────────────────────────┘                    │
└──────────────────────────────────────────────────────────────┘
```

## 2. 新增文件清单（12 个）

| 层级 | 文件路径 | 功能 |
|------|----------|------|
| 追踪模型 | `observability/model/TraceSpan.java` | Span 模型（操作类型/耗时/token/子 Span 树） |
| 追踪模型 | `observability/model/TraceRecord.java` | 单条 Trace 完整记录（含汇总统计） |
| 追踪服务 | `observability/TracingService.java` | Trace/Span 生命周期 + 内存环形缓冲 + 查询 |
| 成本统计 | `observability/CostTracker.java` | Token 估算 + 成本计算（中文/英文分别计算） |
| LLM代理 | `observability/ObservableChatModel.java` | ChatModel 代理，每次 chat() 自动创建 llm Span |
| AOP 切面 | `observability/TracingAspect.java` | 自动拦截 Agent/编排/工具调用（阶段级 Span） |
| 查询 API | `observability/ObservabilityController.java` | /v1/metrics + /v1/traces 接口 |
| 评测模型 | `evaluation/model/EvalCase.java` | 评测用例（输入/期望工具/期望关键词） |
| 评测模型 | `evaluation/model/EvalResult.java` | 单条评测结果 |
| 评测模型 | `evaluation/model/EvalReport.java` | 评测报告（成功率/耗时/成本/失败原因） |
| 评测服务 | `evaluation/EvaluationService.java` | 批量评测执行引擎 |
| 评测 API | `evaluation/EvaluationController.java` | POST /v1/eval/run 接口 |
| 测试集 | `resources/eval/regression-test-cases.json` | 10 条回归测试用例 |
| 测试 | `test/.../ObservabilityTest.java` | 12 个单元测试 |

## 3. 如何调用

### 3.1 查看汇总指标

```bash
curl http://localhost:8080/v1/metrics
```

返回：

```json
{
  "totalRequests": 15,
  "successCount": 13,
  "failCount": 2,
  "successRate": "86.7%",
  "avgDurationMs": 2450,
  "p99DurationMs": 5200,
  "totalTokenEstimate": 18500,
  "totalCostEstimate": "¥0.0296",
  "totalLlmCalls": 42,
  "totalToolCalls": 28,
  "failReasons": {
    "LLM 连接超时": 1,
    "工具 query_logs 执行失败": 1
  }
}
```

### 3.2 查看最近的 Trace 列表

```bash
curl http://localhost:8080/v1/traces?limit=5
```

返回每条 Trace 的概要（traceId、耗时、token、成本、成功/失败）。

### 3.3 查看单条 Trace 详情（含 Span 树）

```bash
curl http://localhost:8080/v1/traces/{traceId}
```

返回完整的 Span 树，可以看到每个阶段（Planner/Executor/Reviewer）的耗时和 token。

### 3.4 执行离线评测

```bash
curl -X POST http://localhost:8080/v1/eval/run
```

返回：

```json
{
  "totalCases": 10,
  "passedCases": 9,
  "failedCases": 1,
  "passRate": "90.0%",
  "avgDurationMs": 2100,
  "totalTokenEstimate": 12000,
  "totalCostEstimate": "¥0.0192",
  "failures": [
    {"caseId": "TC-005", "input": "你好", "checks": ["❌ 未调用期望工具"]}
  ],
  "results": [ ... ]
}
```

## 4. 配置说明

```yaml
# application.yml Day 5 新增
observability:
  trace-buffer-size: 200               # 内存中保留最近多少条 Trace
  cost:
    input-price-per-1k: 0.0008         # 输入价格：元/千token (qwen-plus)
    output-price-per-1k: 0.002         # 输出价格：元/千token (qwen-plus)
```

## 5. 回归测试集（10 条用例）

| ID | 分类 | 模式 | 输入 | 期望工具 |
|----|------|------|------|----------|
| TC-001 | log_query | agent | 最近有什么 ERROR 级别的日志？ | query_logs |
| TC-002 | ticket_query | agent | 有哪些高优先级的未解决工单？ | query_tickets |
| TC-003 | ticket_query | agent | 张三负责的工单有哪些？ | query_tickets |
| TC-004 | log_query | agent | order-service 最近有什么告警？ | query_logs |
| TC-005 | chat | agent | 你好，你是谁？ | （无工具） |
| TC-006 | mixed | orchestrate | 分析系统健康状况 | query_logs, query_tickets |
| TC-007 | mixed | orchestrate | 张三工单及相关日志 | query_tickets, query_logs |
| TC-008 | ticket_query | orchestrate | 紧急工单列表及优先级分析 | query_tickets |
| TC-009 | log_query | orchestrate | gateway 和 user-service 日志 | query_logs |
| TC-010 | db_query | agent | 知识库里有没有 Spring Boot？ | query_database |

## 6. 验证结果

| 项目 | 结果 |
|------|------|
| 编译 | ✅ 通过 |
| Day 3 MCP 测试 (10 个) | ✅ 全部通过 |
| Day 4 编排测试 (7 个) | ✅ 全部通过 |
| Day 5 可观测测试 (12 个) | ✅ 全部通过 |
| 合计 | ✅ **29 个测试全部通过，零回归** |

### 测试用例明细

| 测试用例 | 验证内容 | 状态 |
|----------|----------|------|
| `testTraceSpanLifecycle` | Span start/end 耗时计算 | ✅ |
| `testTraceSpanError` | Span 错误记录 | ✅ |
| `testTraceSpanAttributes` | attr/withTokenCost 链式调用 | ✅ |
| `testTraceSpanChildren` | 父子 Span 关联 | ✅ |
| `testTracingServiceLifecycle` | TracingService 完整生命周期 | ✅ |
| `testTracingServiceMetrics` | getMetrics 汇总统计 | ✅ |
| `testTracingServiceFail` | failTrace 错误记录 | ✅ |
| `testTraceRecordSummary` | TraceRecord 递归汇总 Span 树 | ✅ |
| `testCostTrackerChinese` | 中文 Token 估算 | ✅ |
| `testCostTrackerEnglish` | 英文 Token 估算 | ✅ |
| `testCostTrackerEmpty` | 空文本处理 | ✅ |
| `testCostTrackerEstimate` | estimate 返回值 | ✅ |

## 7. 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| LLM 调用观测 | ObservableChatModel 代理层 | 拦截全部 7 处 chatModel.chat() 调用，精确记录每次 Token/Cost |
| Service 注入类型 | 从 QwenChatModel 改为 ChatModel 接口 | 面向接口编程，解耦具体实现，方便包装观测层 |
| Trace 存储 | 内存环形缓冲（ConcurrentLinkedDeque） | 无外部依赖，适合学习项目，生产环境可替换为 Jaeger/Zipkin |
| 线程上下文 | ThreadLocal | Spring MVC 单请求单线程，简单可靠 |
| Token 估算 | 字符数启发式（中文 0.7，英文 /4） | 通义千问不一定返回 usage，自主估算保证一致性 |
| AOP 职责 | 只负责阶段级 Span，LLM Span 交给 ObservableChatModel | 职责分离，避免重复计算 |
| 评测框架 | JSON 用例 + Service + REST API | 可 curl 触发，也可嵌入 CI/CD |
| 成本定价 | 配置化 | application.yml 可按模型调整 |

## 8. 与 Day 1-4 的衔接

```
Day 1          Day 2         Day 3         Day 4          Day 5
结构化输出      RAG 知识库     MCP 工具      三角色编排       可观测与评测
─────────     ─────────    ─────────     ─────────      ─────────────
                                                        TracingAspect
                                                        (AOP 阶段级 Span)
                                                             │
ExtractCtrl   AskCtrl      AgentCtrl    OrchCtrl        ─────┼─────
     │            │             │            │          ObservCtrl  EvalCtrl
ExtrSvc       RagSvc       AgentSvc     OrchSvc         /metrics   /eval/run
     │            │             │       ┌───┼───┐       /traces
  LlmClient  RetrievalSvc  ToolRegistry P   E   R
                    │        ┌──┼──┐
                 KnowledgeBase  query_*  (3 tools)
                    │            │
               ──── PostgreSQL ────

  ↑ 所有 Service 的 chatModel.chat() 调用 ↑
  均经过 ObservableChatModel 代理，自动记录 llm Span + Token + Cost
  注入类型: QwenChatModel → ChatModel 接口（Day 5 改进）
```

Day 5 的 AOP 切面无侵入地织入 Day 3 Agent 和 Day 4 编排的所有调用路径；
评测框架同时覆盖 agent 和 orchestrate 两种模式。
