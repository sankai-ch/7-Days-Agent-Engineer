---

# Day 4: Agent 编排 — 完成总结

## 1. 做了什么

将 Day 3 的**单 Agent 模式**（ReAct）升级为 **Planner → Executor → Reviewer → Summarizer** 四阶段流水线，实现三角色协作编排。

### 流水线示意

```
用户任务: "分析系统健康状况，包括错误日志和未解决的高优工单"
   │
   ▼
① Planner   → 拆解为 3 步: 查ERROR日志 / 查HIGH工单 / 查CRITICAL工单
② Executor  → 逐步调用工具，收集结果（单步失败不中断）
③ Reviewer  → 评分 88/100, 裁决 PASS（或要求补充后重新复核）
④ Summarizer → 综合所有结果生成自然语言回答
```

## 2. 新增文件清单（10 个）

| 层级 | 文件路径 | 功能 |
|------|----------|------|
| 模型 | `orchestration/model/PlanStep.java` | 计划步骤（工具名+参数+目的） |
| 模型 | `orchestration/model/StepResult.java` | 步骤执行结果（成功/失败+耗时） |
| 模型 | `orchestration/model/ReviewVerdict.java` | 复核裁决（PASS/NEEDS_SUPPLEMENT/FAIL+评分） |
| 模型 | `orchestration/model/OrchestrationResponse.java` | 编排完整响应（全链路信息） |
| 角色 | `orchestration/role/PlannerService.java` | Planner：LLM 拆解任务为多步计划 |
| 角色 | `orchestration/role/ExecutorService.java` | Executor：按计划逐步调用 MCP 工具 |
| 角色 | `orchestration/role/ReviewerService.java` | Reviewer：LLM 质量评分+补充建议 |
| 调度 | `orchestration/OrchestrationService.java` | 流水线编排调度器 |
| API | `orchestration/OrchestrationController.java` | REST 接口 `POST /v1/orchestrate` |
| 测试 | `test/.../OrchestrationTest.java` | 7 个单元测试 |

## 3. 配置变更

`application.yml` 新增：

```yaml
orchestration:
  max-supplement-rounds: 1   # Reviewer 要求补充时最多允许几轮
```

## 4. 如何调用

### 4.1 三角色编排（Day 4 新增，适合复杂任务）

```bash
curl -X POST http://localhost:8080/v1/orchestrate \
  -H "Content-Type: application/json" \
  -d '{"query": "分析系统健康状况，包括错误日志和未解决的高优工单"}'
```

返回结构：

```json
{
  "answer": "综合分析如下：...",
  "plan": [
    {"stepId":1, "tool":"query_logs",    "arguments":{"level":"ERROR"},                       "purpose":"查询错误日志"},
    {"stepId":2, "tool":"query_tickets", "arguments":{"status":"OPEN","priority":"HIGH"},      "purpose":"查询高优工单"},
    {"stepId":3, "tool":"query_tickets", "arguments":{"status":"OPEN","priority":"CRITICAL"},  "purpose":"查询紧急工单"}
  ],
  "stepResults": [
    {"stepId":1, "tool":"query_logs",    "success":true, "durationMs":85,  "result":{...}},
    {"stepId":2, "tool":"query_tickets", "success":true, "durationMs":120, "result":{...}},
    {"stepId":3, "tool":"query_tickets", "success":true, "durationMs":95,  "result":{...}}
  ],
  "review": {"verdict":"PASS", "qualityScore":88, "comment":"信息充分"},
  "supplementResults": [],
  "totalDurationMs": 3200,
  "success": true,
  "mode": "planner-executor-reviewer"
}
```

### 4.2 单 Agent（Day 3 原有，适合简单查询，仍可用）

```bash
curl -X POST http://localhost:8080/v1/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"query": "最近有什么 ERROR 日志？"}'
```

### 4.3 两种模式对比

| 测试任务 | 单 Agent (Day3) | 三角色编排 (Day4) |
|----------|-----------------|-------------------|
| "查一下 ERROR 日志" | ✅ 1 步完成 | ✅ 1 步完成（简单任务自动退化） |
| "分析系统健康状况" | ⚠️ 只调 1 个工具 | ✅ 自动拆解为 2-3 步，覆盖多维度 |
| "张三负责的工单及相关日志" | ⚠️ 只查工单或日志 | ✅ 先查工单再查日志，交叉分析 |
| 某步骤执行失败 | ❌ 整体失败 | ✅ 跳过继续 + Reviewer 判断是否补充 |

## 5. 验证结果

| 项目 | 结果 |
|------|------|
| 编译 | ✅ 通过 |
| Day 3 MCP 测试 (10 个) | ✅ 全部通过，无回归 |
| Day 4 编排测试 (7 个) | ✅ 全部通过 |
| 合计 | ✅ 17 个测试全部通过 |

### 测试用例明细

| 测试用例 | 验证内容 | 状态 |
|----------|----------|------|
| `testPlanStep` | PlanStep 模型正确构建 | ✅ |
| `testStepResult` | StepResult.ok / .fail 工厂方法 | ✅ |
| `testReviewVerdict` | PASS / NEEDS_SUPPLEMENT / FAIL 三种裁决 | ✅ |
| `testOrchestrationResponse` | success / failure 响应构建 | ✅ |
| `testExecutorSuccess` | Executor 正常执行多步计划 | ✅ |
| `testExecutorPartialFailure` | 单步失败不中断后续步骤 | ✅ |
| `testReviewerAllFail` | 全部失败直接判 FAIL | ✅ |

## 6. 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 角色间通信 | 内存直调（同进程三个 Service） | 无需引入消息队列，简洁高效 |
| Executor 容错 | 单步失败继续执行后续步骤 | 不因部分失败丢失已有结果 |
| Reviewer 实现 | LLM 评分 + 规则回退双保险 | LLM 异常时退化为成功率简单计算 |
| 补充循环上限 | 可配置（默认 1 轮） | 防止无限补充消耗 token |
| Planner 步骤上限 | 提示词约束 1-5 步 | 平衡任务粒度与 token 成本 |
| 与 Day 3 共存 | 两个接口并行提供 | 简单任务用单 Agent，复杂任务用编排 |
