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

## 运行

```bash
cd /Users/sankai/Documents/obsidian/everything/AI学习/7-Days-Agent-Engineer
mvn spring-boot:run
```

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
