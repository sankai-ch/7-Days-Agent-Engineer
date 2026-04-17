# Agent Engineer — 7 天速通实战项目

> 一个"可演示、可评测、可扩展"的生产雏形 Agent 系统。
> 涵盖结构化输出、RAG 知识库、MCP 工具调用、多角色编排、可观测性、安全防护全链路。

## 架构总览

```
                         用户请求
                            │
            ┌───────────────┼───────────────┐
            ▼               ▼               ▼
     POST /v1/extract  POST /v1/agent  POST /v1/orchestrate
     (结构化输出)       /chat(单Agent)  (三角色编排)
            │               │               │
            │          ┌────┴────┐     ┌────┴────┐
            │          │Security │     │Security │
            │          │ Chain   │     │ Chain   │
            │          └────┬────┘     └────┬────┘
            ▼               ▼               ▼
    ┌──────────────┐ ┌────────────┐ ┌──────────────────┐
    │Extraction    │ │ Agent      │ │ Orchestration     │
    │Service       │ │ Service    │ │ Service           │
    │(Day1)        │ │ (Day3)     │ │ ┌─────┬─────┐    │
    └──────────────┘ └─────┬──────┘ │ │Plan │Exec │Rev ││
                           │        │ │ner  │utor │iew ││
                           │        │ └──┬──┴──┬──┴──┘ │
                           ▼        └────┼─────┼───────┘
                    ┌──────────────┐     │     │
                    │McpToolRegistry│◄────┘     │
                    │(工具注册中心) │           │
                    └──┬───┬───┬──┘           │
                       │   │   │              │
              ┌────────┤   │   ├────────┐     │
              ▼        ▼   │   ▼        │     │
         query_    query_  │ query_     │     │
         database  logs    │ tickets    │     │
              │        │   │   │        │     │
              ▼        ▼   ▼   ▼        │     │
         ┌─────────────────────────┐    │     │
         │     PostgreSQL          │    │     │
         │  knowledge_segments     │    │     │
         │  app_logs               │    │     │
         │  work_tickets           │    │     │
         └─────────────────────────┘    │     │
                                        │     │
    ┌───── 可观测层 ──────┐    ┌── 安全层 ──┐  │
    │ TracingService      │    │ Injection  │  │
    │ CostTracker         │    │ Guard      │  │
    │ TracingAspect (AOP) │    │ Masker     │  │
    │ /v1/metrics         │    │ Whitelist  │  │
    │ /v1/traces          │    │ CircuitBkr │  │
    └─────────────────────┘    └────────────┘
    ┌─── RAG 知识库(Day2)──┐   ┌─── MCP Server(Day3)──┐
    │ POST /v1/ask         │   │ POST /mcp/rpc         │
    │ RagService           │   │ JSON-RPC 2.0          │
    │ RetrievalService     │   │ Bearer Token 鉴权     │
    │ pgvector 向量检索    │   │ GET /mcp/info         │
    └──────────────────────┘   └───────────────────────┘
```

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.5.4 |
| LLM | 通义千问 qwen-plus（通过 LangChain4j DashScope 适配器） |
| Embedding | 通义千问 text-embedding-v3（1024 维） |
| 向量库 | PostgreSQL + pgvector |
| 协议 | MCP (Model Context Protocol) / JSON-RPC 2.0 |
| 安全 | Prompt Injection 防护、敏感信息脱敏、熔断器、限流 |
| 可观测 | 自研 Tracing (Span 树) + Token/Cost 估算 + AOP 切面 |
| 测试 | JUnit 5 + MockMvc，48 个测试用例 |

## 快速启动

### 环境要求

- JDK 17+
- Maven 3.6+
- PostgreSQL（需安装 pgvector 扩展）

### 一键启动

```bash
# 1. 克隆项目
git clone <repo-url> && cd 7-Days-Agent-Engineer

# 2. 配置（修改 src/main/resources/application.yml）
#    - dashscope.api-key: 你的通义千问 API Key
#    - spring.datasource.*: 你的 PostgreSQL 连接信息

# 3. 编译 & 启动
mvn compile -q && mvn spring-boot:run

# 4. 验证服务
curl http://localhost:8080/health
# 返回: {"status":"ok"}
```

### 运行测试

```bash
mvn test
# 预期: 48 个测试全部通过
```

## API 接口一览

### Day 1 — 结构化输出

```bash
# 健康检查
GET /health

# 文本结构化提取
POST /v1/extract
Body: {"userText": "How can I deploy this service ASAP?"}
```

### Day 2 — RAG 知识库问答

```bash
# 知识库问答（带引用）
POST /v1/ask
Body: {"question": "MCP 在 Agent 里的作用是什么？"}
```

### Day 3 — 单 Agent + MCP Server

```bash
# 单 Agent 对话（自动选工具）
POST /v1/agent/chat
Body: {"query": "最近有什么 ERROR 日志？"}

# MCP Server 信息
GET /mcp/info

# MCP RPC 调用（需 Token）
POST /mcp/rpc
Header: Authorization: Bearer mcp-day3-token-2024
Body: {"jsonrpc":"2.0","id":1,"method":"tools/list"}
```

### Day 4 — 三角色编排

```bash
# 编排模式（Planner→Executor→Reviewer→汇总）
POST /v1/orchestrate
Body: {"query": "分析系统健康状况，包括错误日志和未解决的高优工单"}
```

### Day 5 — 可观测与评测

```bash
# 汇总指标（成功率/时延/成本/失败原因）
GET /v1/metrics

# 最近链路追踪列表
GET /v1/traces?limit=10

# 单条 Trace 详情（含 Span 树）
GET /v1/traces/{traceId}

# 执行离线评测（10 条回归用例）
POST /v1/eval/run
```

### Day 6 — 安全状态

```bash
# 安全状态（熔断器状态 + 白名单）
GET /v1/security/status
```

## 项目结构

```
src/main/java/com/sankai/agent/
├── Day1Application.java              # Spring Boot 启动类
│
├── api/                              # ── Day 1: 结构化输出 ──
│   ├── ExtractController.java        #   POST /v1/extract
│   ├── AskController.java            #   POST /v1/ask (Day 2)
│   └── GlobalExceptionHandler.java   #   全局异常处理
├── client/                           #   LLM 客户端抽象
├── model/                            #   请求/响应模型
├── service/                          #   ExtractionService, RagService
├── entity/                           #   JPA 实体
├── repository/                       #   数据访问层
├── config/                           #   LangChain4j 配置
│
├── mcp/                              # ── Day 3: MCP Server ──
│   ├── protocol/                     #   JSON-RPC 2.0 协议模型
│   ├── server/                       #   McpServerController, 鉴权, 工具注册
│   └── tool/                         #   3 个工具实现
├── agent/                            #   AgentService + Controller
│
├── orchestration/                    # ── Day 4: 三角色编排 ──
│   ├── OrchestrationService.java     #   编排调度器
│   ├── OrchestrationController.java  #   REST API
│   ├── model/                        #   PlanStep, StepResult, ReviewVerdict
│   └── role/                         #   Planner, Executor, Reviewer
│
├── observability/                    # ── Day 5: 可观测 ──
│   ├── TracingService.java           #   链路追踪核心
│   ├── CostTracker.java              #   Token/Cost 估算
│   ├── TracingAspect.java            #   AOP 切面
│   ├── ObservabilityController.java  #   /v1/metrics, /v1/traces
│   └── model/                        #   TraceSpan, TraceRecord
│
├── evaluation/                       # ── Day 5: 离线评测 ──
│   ├── EvaluationService.java        #   批量评测引擎
│   ├── EvaluationController.java     #   POST /v1/eval/run
│   └── model/                        #   EvalCase, EvalResult, EvalReport
│
└── security/                         # ── Day 6: 安全防护 ──
    ├── PromptInjectionGuard.java     #   注入检测
    ├── SensitiveDataMasker.java      #   敏感信息脱敏
    ├── ToolWhitelistGuard.java       #   工具白名单 + 限流
    ├── ResilienceGuard.java          #   熔断器 + 幂等
    └── SecurityChain.java            #   安全检查链

src/test/java/com/sankai/agent/       # 48 个测试用例
├── McpServerTest.java                #   MCP 协议测试 (10)
├── OrchestrationTest.java            #   编排流水线测试 (7)
├── ObservabilityTest.java            #   可观测性测试 (12)
└── SecurityTest.java                 #   安全攻防测试 (19)

src/main/resources/
├── application.yml                   # 全量配置
├── schema.sql                        # 数据库 DDL + 示例数据
├── knowledge/*.md                    # RAG 知识库文档
└── eval/regression-test-cases.json   # 10 条回归测试用例
```

## 每日产出对比

| Day | 主题 | 核心交付 | 新增文件 | 测试数 |
|-----|------|----------|----------|--------|
| 1 | 结构化输出 | ExtractController + JSON Schema 校验 + 重试 | 9 | 3 |
| 2 | RAG 知识库 | pgvector 向量检索 + 引用回溯 + /v1/ask | 7 | 2 |
| 3 | MCP 工具 | MCP Server + 3 工具 + Bearer 鉴权 + Agent | 14 | 10 |
| 4 | 三角色编排 | Planner/Executor/Reviewer 流水线 | 10 | 7 |
| 5 | 可观测 | Tracing + Token/Cost + AOP + 离线评测 | 12 | 12 |
| 6 | 安全防护 | 注入防护 + 脱敏 + 白名单 + 熔断 + 限流 | 6 | 19 |
| 7 | 项目封装 | README + 启动脚本 + 总文档 | 4 | - |
| **合计** | | | **62+** | **48 (全通过)** |

## 单 Agent vs 三角色编排 — 模式对比

| 维度 | 单 Agent (`/v1/agent/chat`) | 三角色编排 (`/v1/orchestrate`) |
|------|----------------------------|-------------------------------|
| 工具调用 | 单工具单次 | 多工具多步，自动拆解 |
| 质量控制 | 无 | Reviewer 评分 + 裁决 + 补充 |
| 复杂任务 | 能力有限 | Planner 自动分解 1-5 步 |
| 容错 | 单步失败即终止 | 单步失败继续 + 补充循环 |
| 可观测性 | 单层 | 全链路（计划/执行/复核/汇总） |
| 适用场景 | 简单查询 | 多维度综合分析 |

## 一周产出清单

- [x] 可运行 Agent 服务（含工具调用）
- [x] 1 个 MCP Server（3 个工具: query_database, query_logs, query_tickets）
- [x] 1 套 RAG 知识库流程（pgvector + LangChain4j）
- [x] 1 套最小评测与回归脚本（10 条用例 + POST /v1/eval/run）
- [x] 1 份项目文档（架构、指标、风险、下一步）—— 见 `doc/` 目录

## 文档索引

| 文档 | 说明 |
|------|------|
| [Day 2 RAG 计划](doc/day2-execute-plan.md) | RAG MVP 设计文档 |
| [Day 3 MCP 总结](doc/day3-mcp-summary.md) | MCP Server 架构与设计 |
| [Day 4 编排总结](doc/day4-completion-report.md) | 三角色编排设计与对比 |
| [Day 5 可观测总结](doc/day5-completion-report.md) | Tracing + 评测框架 |
| [Day 6 安全总结](doc/day6-completion-report.md) | 安全防护 19 条攻防用例 |
| [Day 7 项目总文档](doc/day7-completion-report.md) | 最终架构 + 指标 + 风险 + 面试叙事 |
