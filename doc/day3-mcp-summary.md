---

# Day 3: MCP 实战 — 设计与实现总结

## 1. 文档信息
*   **版本**：v1.0
*   **目标**：构建 1 个 MCP Server（含 3 个工具）+ 1 个具备工具调用能力的智能 Agent。
*   **核心组件**：MCP 协议（JSON-RPC 2.0）+ LangChain4j + 通义千问 + PostgreSQL。
*   **验收标准**：Agent 能稳定调用工具并返回结构化结果。

## 2. 业务概述

Day 3 的核心任务是实现 **MCP（Model Context Protocol）** —— 一种让 LLM 与外部工具交互的标准化协议。

通过本日的实战，项目新增了：
- 1 个完整的 MCP Server，遵循 JSON-RPC 2.0 规范
- 3 个业务工具：查数据库、查日志、查工单
- 1 个 Agent 编排服务，能根据用户自然语言自动选择工具
- Bearer Token 鉴权机制
- 10 个单元/集成测试

## 3. 架构设计

### 3.1 整体架构图

```
用户请求                                            外部 MCP 客户端
   │                                                  │
   ▼                                                  ▼
┌────────────────────────┐              ┌──────────────────────────────┐
│  POST /v1/agent/chat   │              │   POST /mcp/rpc              │
│   AgentController      │              │   McpServerController        │
└────────┬───────────────┘              │   (JSON-RPC 2.0 over HTTP)   │
         │                              └───────────┬──────────────────┘
         ▼                                          │
┌────────────────────────┐                          │
│    AgentService        │       ┌──────────────────┘
│  ┌─────────────────┐   │       │           ▲
│  │ 第1轮: LLM分析   │   │       │    McpAuthFilter
│  │ 选择工具+参数    │   │       │    (Bearer Token 鉴权)
│  └───────┬─────────┘   │       │
│          ▼              │       ▼
│  ┌─────────────────┐   │  ┌────────────────────┐
│  │ McpToolRegistry │◄──┼──┤  McpToolRegistry   │
│  │ 查找并调用工具   │   │  │  工具注册/发现/调用 │
│  └───────┬─────────┘   │  └────────┬───────────┘
│          │              │           │
│  ┌───────┼──────────────┼───────────┤
│  │       ▼              │           ▼
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────┐
│  │  │query_    │ │query_    │ │query_        │
│  │  │database  │ │logs      │ │tickets       │
│  │  │(查库)    │ │(查日志)   │ │(查工单)      │
│  │  └──────────┘ └──────────┘ └──────────────┘
│  │       │              │           │
│  │       ▼              ▼           ▼
│  │  ┌────────────────────────────────────┐
│  │  │         PostgreSQL 数据库           │
│  │  │ knowledge_segments | app_logs      │
│  │  │                    | work_tickets  │
│  │  └────────────────────────────────────┘
│  │
│  └──────────────────────┘
│          │
│          ▼
│  ┌─────────────────┐
│  │ 第2轮: LLM汇总   │
│  │ 生成最终回答      │
│  └─────────────────┘
└────────────────────────┘
```

### 3.2 分层结构

项目采用清晰的四层架构：

```
com.sankai.agent
├── mcp/                          # ===== MCP 层（Day 3 新增）=====
│   ├── protocol/                 # 协议模型（JSON-RPC 2.0）
│   │   ├── JsonRpcRequest.java       # 请求模型
│   │   ├── JsonRpcResponse.java      # 响应模型（含标准错误码）
│   │   ├── McpToolInfo.java          # 工具描述模型（name + schema）
│   │   └── McpInitializeResult.java  # MCP 初始化握手响应
│   ├── server/                   # 服务端核心
│   │   ├── McpServerController.java  # MCP HTTP 端点（路由分发）
│   │   ├── McpToolRegistry.java      # 工具注册中心（自动发现）
│   │   └── McpAuthFilter.java       # Bearer Token 鉴权过滤器
│   └── tool/                     # 工具实现
│       ├── McpTool.java              # 工具统一接口（开闭原则）
│       ├── DatabaseQueryTool.java    # 工具1: 知识库查询
│       ├── LogQueryTool.java         # 工具2: 日志查询
│       └── TicketQueryTool.java      # 工具3: 工单查询
├── agent/                        # ===== Agent 层（Day 3 新增）=====
│   ├── AgentService.java            # Agent 核心编排（ReAct 模式）
│   ├── AgentController.java         # Agent REST API
│   └── model/
│       ├── AgentRequest.java         # 请求模型
│       └── AgentResponse.java        # 响应模型（含工具调用链）
├── api/                          # ===== API 层（Day 1&2）=====
├── service/                      # ===== 业务层（Day 1&2）=====
├── config/                       # ===== 配置层 =====
└── ...
```

## 4. 核心设计详解

### 4.1 MCP 协议实现

MCP 基于 **JSON-RPC 2.0** 标准，本项目采用 **Streamable HTTP** 传输方式（单一 POST 端点）。

#### 支持的 MCP 方法

| 方法 | 说明 | 请求示例 |
|------|------|----------|
| `initialize` | 初始化连接，返回服务器信息和能力声明 | `{"method":"initialize"}` |
| `ping` | 健康检查 | `{"method":"ping"}` |
| `tools/list` | 列出所有可用工具及参数 Schema | `{"method":"tools/list"}` |
| `tools/call` | 调用指定工具 | `{"method":"tools/call","params":{"name":"...","arguments":{...}}}` |

#### 协议流程

```
Client                              Server
  │                                    │
  │─── initialize ────────────────────>│  握手：获取服务器信息
  │<── {protocolVersion, capabilities}─│
  │                                    │
  │─── tools/list ────────────────────>│  发现：获取可用工具列表
  │<── {tools: [{name, schema}, ...]}──│
  │                                    │
  │─── tools/call ────────────────────>│  调用：执行工具
  │    {name: "query_logs",            │
  │     arguments: {level: "ERROR"}}   │
  │<── {content: [{text: "..."}]}──────│  结果：结构化数据
  │                                    │
```

### 4.2 工具注册表模式（McpToolRegistry）

采用 **注册表模式（Registry Pattern）**，利用 Spring 依赖注入实现零侵入扩展：

```java
// 新增工具只需两步：
// 1. 实现 McpTool 接口
// 2. 加上 @Component 注解
@Component
public class MyNewTool implements McpTool {
    public String getName() { return "my_tool"; }
    public McpToolInfo getToolInfo() { ... }
    public Object execute(Map<String, Object> args) { ... }
}
// 无需修改注册中心或服务端的任何代码 → 满足开闭原则
```

### 4.3 三个工具详解

#### 工具1: `query_database` — 知识库查询

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `keyword` | string | 是 | 搜索关键词，模糊匹配文档内容 |
| `limit` | integer | 否 | 返回数量上限（默认5，最大20） |

复用 Day 2 RAG 的 `knowledge_segments` 表，通过 SQL ILIKE 进行全文搜索。

#### 工具2: `query_logs` — 日志查询

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `level` | string | 否 | 日志级别：INFO / WARN / ERROR |
| `service` | string | 否 | 服务名称过滤 |
| `keyword` | string | 否 | 日志内容关键词 |
| `limit` | integer | 否 | 返回数量上限（默认10，最大50） |

查询 `app_logs` 表，支持多条件组合动态 SQL。

#### 工具3: `query_tickets` — 工单查询

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `status` | string | 否 | OPEN / IN_PROGRESS / RESOLVED / CLOSED |
| `priority` | string | 否 | LOW / MEDIUM / HIGH / CRITICAL |
| `assignee` | string | 否 | 负责人姓名 |
| `keyword` | string | 否 | 标题/描述关键词 |
| `limit` | integer | 否 | 返回数量上限（默认10，最大50） |

查询 `work_tickets` 表，支持多维度组合过滤。

### 4.4 Agent 编排（ReAct 模式）

Agent 使用 **ReAct（Reasoning + Acting）** 模式：

```
用户: "最近有什么高优先级的未解决工单？"
  │
  ▼
[第1轮 LLM] 推理(Reasoning)
  → 思考: "用户要查高优先级未解决工单，需要调用 query_tickets"
  → 输出: {"tool_call": {"name": "query_tickets", "arguments": {"status": "OPEN", "priority": "HIGH"}}}
  │
  ▼
[执行(Acting)] 调用 query_tickets 工具
  → 获取: 3 条匹配工单记录
  │
  ▼
[第2轮 LLM] 汇总(Summarizing)
  → 根据工具结果生成自然语言回答
  → "当前有 3 个高优先级未解决工单: 1. 登录页面偶发502... 2. Redis连接池..."
```

### 4.5 鉴权机制

```
请求 → McpAuthFilter
         │
         ├── 路径不是 /mcp/** → 放行
         ├── 未配置 auth-token → 跳过鉴权（开发模式）
         ├── Header 无 Authorization → 401
         ├── Token 不匹配 → 401
         └── Token 匹配 → 放行到 McpServerController
```

## 5. 数据库设计

### 5.1 新增表（Day 3）

```sql
-- 应用日志表
CREATE TABLE app_logs (
    id          BIGSERIAL PRIMARY KEY,
    level       VARCHAR(10)  NOT NULL,    -- INFO / WARN / ERROR
    service     VARCHAR(100) NOT NULL,    -- 服务名称
    message     TEXT         NOT NULL,    -- 日志消息
    trace_id    VARCHAR(64),             -- 链路追踪 ID
    created_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 工单表
CREATE TABLE work_tickets (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    priority    VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    assignee    VARCHAR(100),
    created_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
```

### 5.2 示例数据

- `app_logs`：10 条模拟日志（覆盖 INFO/WARN/ERROR 三个级别，涉及 4 个服务）
- `work_tickets`：8 条模拟工单（覆盖所有状态和优先级，涉及 4 位负责人）

## 6. 配置说明（application.yml）

```yaml
# MCP Server 配置
mcp:
  server:
    name: agent-mcp-server          # 服务器名称（initialize 时返回）
    version: 1.0.0                  # 服务器版本号
    auth-token: mcp-day3-token-2024 # 鉴权 Token（留空则跳过鉴权）

# Agent 配置
agent:
  max-tool-rounds: 3               # 单次对话最大工具调用轮次
```

## 7. API 接口文档

### 7.1 Agent 对话接口

```bash
POST /v1/agent/chat
Content-Type: application/json

{
  "query": "最近有什么 ERROR 级别的日志？"
}
```

响应示例：
```json
{
  "answer": "最近有 3 条 ERROR 级别的日志：\n1. 订单支付回调超时...\n2. 上游服务连接被拒绝...\n3. Redis连接池耗尽...",
  "toolCalls": [
    {
      "tool": "query_logs",
      "arguments": {"level": "ERROR", "limit": 10},
      "result": { "total": 3, "results": [...] },
      "durationMs": 85,
      "success": true
    }
  ],
  "thinking": "用户想查看最近的 ERROR 日志，需要调用 query_logs 工具...",
  "success": true
}
```

### 7.2 MCP Server 接口

```bash
# 查看服务器信息
GET /mcp/info

# MCP 协议调用（需鉴权）
POST /mcp/rpc
Authorization: Bearer mcp-day3-token-2024
Content-Type: application/json

# 示例：列出工具
{"jsonrpc":"2.0","id":1,"method":"tools/list"}

# 示例：调用工具
{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"query_tickets","arguments":{"status":"OPEN","priority":"HIGH"}}}
```

## 8. 测试覆盖

| 测试用例 | 验证内容 | 状态 |
|----------|----------|------|
| `testInitialize` | initialize 返回服务器信息和能力声明 | ✅ |
| `testPing` | ping 健康检查返回空结果 | ✅ |
| `testToolsList` | tools/list 返回已注册工具列表 | ✅ |
| `testToolsCall` | tools/call 调用工具并返回结构化结果 | ✅ |
| `testToolsCallNotFound` | 调用不存在的工具返回 isError=true | ✅ |
| `testToolsCallMissingName` | 缺少 name 参数返回 INVALID_PARAMS 错误 | ✅ |
| `testUnknownMethod` | 未知方法返回 METHOD_NOT_FOUND (-32601) | ✅ |
| `testAuthNoToken` | 无 Token 返回 401 Unauthorized | ✅ |
| `testAuthWrongToken` | 错误 Token 返回 401 Unauthorized | ✅ |
| `testInfoEndpoint` | /mcp/info 返回服务器状态 | ✅ |

## 9. 新增文件清单

| 文件 | 行数 | 功能 |
|------|------|------|
| `mcp/protocol/JsonRpcRequest.java` | ~70 | JSON-RPC 2.0 请求模型 |
| `mcp/protocol/JsonRpcResponse.java` | ~95 | JSON-RPC 2.0 响应（含错误码常量） |
| `mcp/protocol/McpToolInfo.java` | ~55 | 工具描述模型 |
| `mcp/protocol/McpInitializeResult.java` | ~50 | MCP 初始化响应 |
| `mcp/tool/McpTool.java` | ~50 | 工具统一接口 |
| `mcp/tool/DatabaseQueryTool.java` | ~115 | 工具1: 数据库查询 |
| `mcp/tool/LogQueryTool.java` | ~125 | 工具2: 日志查询 |
| `mcp/tool/TicketQueryTool.java` | ~135 | 工具3: 工单查询 |
| `mcp/server/McpToolRegistry.java` | ~95 | 工具注册中心 |
| `mcp/server/McpServerController.java` | ~185 | MCP Server 端点 |
| `mcp/server/McpAuthFilter.java` | ~85 | 鉴权过滤器 |
| `agent/AgentService.java` | ~240 | Agent 核心编排 |
| `agent/AgentController.java` | ~55 | Agent REST API |
| `agent/model/AgentRequest.java` | ~30 | Agent 请求模型 |
| `agent/model/AgentResponse.java` | ~100 | Agent 响应模型（含工具调用记录） |
| `McpServerTest.java` | ~210 | 集成测试（10 个用例） |

## 10. 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| MCP 实现方式 | 手动实现 JSON-RPC 2.0 | 避免与 LangChain4j 的依赖冲突；教育价值更高 |
| 传输方式 | Streamable HTTP (POST) | 比 SSE 更简单直接，适合学习项目 |
| 工具扩展方式 | 注册表模式 + Spring 自动发现 | 满足开闭原则，新增工具零侵入 |
| Agent 模式 | ReAct (推理→执行→汇总) | 两轮 LLM 调用，兼顾准确性和可解释性 |
| 鉴权方式 | Bearer Token + Filter | 简单有效，生产环境可升级为 JWT/OAuth2 |
| 数据库查询方式 | JdbcTemplate + 动态 SQL | 比 JPA 更灵活，适合多条件组合查询 |

## 11. 与 Day 1 & Day 2 的衔接

```
Day 1 (结构化输出)          Day 2 (RAG 知识库)          Day 3 (MCP 实战)
─────────────────        ─────────────────        ─────────────────
  ExtractController         AskController           AgentController
       │                       │                        │
  ExtractionService          RagService              AgentService
       │                       │                        │
    LlmClient             RetrievalService        McpToolRegistry
       │                       │                    ┌───┼───┐
  RuleBasedMockClient    KnowledgeBaseService     query_ query_ query_
                               │                  database logs  tickets
                          SegmentRepository            │
                               │                       │
                          ─── PostgreSQL (pgvector) ────
                          knowledge_segments | app_logs | work_tickets
```

Day 3 的 `query_database` 工具复用了 Day 2 的 `knowledge_segments` 表，
Agent 使用了 Day 1 配置的 LangChain4j + DashScope LLM。
