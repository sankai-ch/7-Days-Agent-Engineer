# 一周速通版（高强度，适合学习能力强）

> 每天建议投入 8-12 小时。
> 目标：7 天内做出一个“可演示、可评测、可扩展”的生产雏形 Agent。

## Day 1：LLM 与结构化输出打底
- 学什么：
  - Prompt 结构、工具调用、JSON Schema 约束输出。
- 做什么：
  - 写一个 API：输入自然语言，输出严格 JSON（带校验和重试）。
- 验收：
  - 100 条输入，结构化输出通过率 > 95%。

## Day 2：RAG MVP
- 学什么：
  - 文档切分、向量检索、重排、引用回溯。
- 做什么：
  - 把你常用技术文档做成一个可问答知识库。
- 验收：
  - 30 个问题里，引用来源正确率达到可用水平。

## Day 3：MCP 实战
- 学什么：
  - MCP Server/Client、工具暴露、鉴权。
- 做什么：
  - 做 1 个 MCP Server，至少 3 个工具（如查库、查日志、查工单）。
- 验收：
  - Agent 能稳定调用工具并返回结构化结果。

## Day 4：Agent 编排
- 学什么：
  - Planner/Executor/Reviewer 三角色协作。
- 做什么：
  - 把复杂任务拆解为“规划->执行->复核->汇总”流水线。
- 验收：
  - 对同一批任务，完成质量高于单 Agent 基线。

## Day 5：可观测与评测
- 学什么：
  - tracing、token/cost 统计、回归测试集。
- 做什么：
  - 加入链路追踪和离线评测脚本。
- 验收：
  - 每次运行能看到成功率、时延、成本、失败原因。

## Day 6：安全与稳定性
- 学什么：
  - Prompt Injection 防护、权限控制、幂等与重试。
- 做什么：
  - 加工具白名单、敏感信息脱敏、异常回退策略。
- 验收：
  - 通过你设计的 10+ 条攻击/异常用例。

## Day 7：项目封装与展示
- 学什么：
  - 项目包装、架构表达、面试/汇报叙事。
- 做什么：
  - 完成 README、架构图、Demo 视频、结果对比表。
- 验收：
  - 形成可面试项目：一键启动 + 示例数据 + 评测结果。

## 一周产出清单（必须交付）
1. 可运行 Agent 服务（含工具调用）。
2. 1 个 MCP Server（>=3 工具）。
3. 1 套 RAG 知识库流程。
4. 1 套最小评测与回归脚本。
5. 1 份项目文档（架构、指标、风险、下一步）。

## 每日固定节奏（建议）
1. 上午：输入学习（2-3 小时）+ 快速编码（2 小时）。
2. 下午：主功能开发（3-4 小时）。
3. 晚上：评测与复盘（2 小时），记录次日优化项。


# Day1 Structured Output API (Java)

Java 版 Day1 可运行脚手架，包含：
- Spring Boot API
- 请求与响应校验
- 结构化输出解析与自动重试
- JUnit + MockMvc 测试

已扩展 Day2（RAG MVP）：
- `POST /v1/ask` 问答接口
- 本地知识库加载（`src/main/resources/knowledge/*.md`）
- 检索召回 + 引用返回（citations）

## 环境要求
- JDK 17+
- Maven 3.9+

接口：
- `GET /health`
- `POST /v1/extract`
- `POST /v1/ask`

请求示例：

```bash
curl -X POST 'http://127.0.0.1:8080/v1/extract' \
  -H 'Content-Type: application/json' \
  -d '{"user_text":"How can I deploy this service ASAP?"}'
```

```bash
curl -X POST 'http://127.0.0.1:8080/v1/ask' \
  -H 'Content-Type: application/json' \
  -d '{"question":"MCP 在 Agent 里的作用是什么？"}'
```

## Day2 用法（RAG MVP）

1. 准备知识库文档：把你的业务知识写入 `src/main/resources/knowledge/*.md`。  
2. 启动服务后调用 `/v1/ask`，系统会做检索并返回引用来源。  
3. 返回字段说明：
   - `answer`：基于检索结果生成的回答
   - `grounded`：是否命中知识库（`true/false`）
   - `citations`：引用片段列表（`docId`、`snippet`、`score`）

示例响应：

```json
{
  "answer": "基于检索结果，我建议先按以下线索处理：...",
  "grounded": true,
  "citations": [
    {
      "docId": "mcp-agent.md",
      "snippet": "MCP（Model Context Protocol）用于让 Agent 统一连接外部工具与数据源...",
      "score": 0.5
    }
  ]
}
```

## 测试

```bash
mvn test
```

## 目录结构

```text
src/main/java/com/example/day1
├─ Day1Application.java
├─ api/
├─ client/
├─ model/
└─ service/
```
