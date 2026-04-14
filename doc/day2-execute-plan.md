---

# PRD: Day 2 - RAG MVP 知识库增强系统

## 1. 文档信息
*   **版本**：v1.1
*   **目标**：在现有 Spring Boot 项目中集成 RAG (检索增强生成) 能力。
*   **核心组件**：PostgreSQL (pgvector) + LangChain4j + 通义千问。
*   **开发者身份**：资深 Java 架构师 / Agent 工程师。

## 2. 业务概述
构建一个能够读取本地 Markdown 格式技术文档，并基于这些文档进行语义检索与专业问答的系统。要求 Agent 在回答时必须引用来源，确保准确性。

## 3. 技术栈约束
*   **核心框架**：JDK 17, Spring Boot 3.4
*   **LLM/Embedding**：
    *   Embedding 模型：通义千问 `text-embedding-v3` (通过 LangChain4j DashScope 适配器)。
    *   Chat 模型：通义千问 `qwen-plus/max`。
*   **数据库/向量库**：PostgreSQL 15+ (必须安装 `pgvector` 扩展)。
*   **连接池**：HikariCP (Spring Boot 默认)。
*   **代码规范**：
    *   必须包含完整的 Javadoc 注释（类、方法、字段）。
    *   采用领域驱动设计逻辑，严格区分 DTO、Entity 和 Service。

## 4. 数据库设计 (DDL)
需要创建支持向量存储的表结构。

```sql
-- 开启向量扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 知识库片段表
CREATE TABLE IF NOT EXISTS knowledge_segments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,                    -- 文本片段内容
    metadata JSONB,                           -- 存储来源文件、行号、标题等
    embedding vector(1024),                   -- text-embedding-v3 输出维度为 1024
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 创建向量索引（提高检索性能）
CREATE INDEX ON knowledge_segments USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

## 5. 核心功能需求

### F1: 数据同步服务 (Data Synchronization)
*   **描述**：扫描指定本地目录（如 `src/main/resources/knowledge/`），解析所有 `.md` 文件。
*   **处理流程**：
    1.  **加载**：读取文件内容。
    2.  **切分 (Chunking)**：使用 `DocumentSplitters.recursive` 按段落切分，确保语义完整，设置 `maxSegmentSize` 为 500 字符，`overlap` 为 50 字符。
    3.  **向量化**：调用通义千问 `text-embedding-v3` 接口。
    4.  **持久化**：批量存入 PostgreSQL。
*   **性能要求**：使用 `CompletableFuture` 或 JDK 21 虚拟线程并行处理 Embedding 请求，提高入库效率。

### F2: 语义检索服务 (Semantic Search)
*   **描述**：将用户问题转化为向量，在数据库中进行余弦相似度计算。
*   **技术要求**：
    *   使用 `PGVector` 存储适配器。
    *   返回 Top-K（建议 K=3）最相关的片段。

### F3: 增强问答接口 (RAG Chat)
*   **接口**：`POST /v1/ask`
*   **逻辑**：
    1.  检索相关知识片段。
    2.  构造系统 Prompt：`"你是一个技术专家。请根据提供的背景信息回答问题。如果信息不足，请回答不知道。回答末尾必须标明引用来源。背景信息：{context}"`。
    3.  调用通义千问 Chat 接口，返回结构化响应。

## 6. 代码结构设计建议

### 实体类 (Entity)
```java
/**
 * 知识库片段实体
 * 对应 PostgreSQL 中的 knowledge_segments 表
 */
@Data
@Entity
@Table(name = "knowledge_segments")
public class KnowledgeSegment {
    @Id
    private UUID id;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;
    
    // 注意：向量字段在 JPA 中通常映射为 float[] 或使用自定义类型
    private float[] embedding;
}
```

### 核心 Service 接口
```java
public interface KnowledgeBaseService {
    /**
     * 同步本地目录下的文档至向量库
     * @param directoryPath 目录路径
     */
    void syncLocalDirectory(String directoryPath);

    /**
     * 基于知识库进行问答
     * @param question 用户问题
     * @return 增强后的回答及引用
     */
    AskResponse ask(String question);
}
```

## 7. 非功能性要求
1.  **连接池配置**：在 `application.yml` 中配置 `maximum-pool-size: 20`，确保并发检索时的稳定性。
2.  **错误处理**：对于 Embedding API 调用失败的情况，需实现重试机制（使用 `Resilience4j`）。
3.  **引用回溯**：回答中必须明确指出知识点来自哪个文件（从 metadata 中提取）。

---

## 8. AI 提示词 (给 Claude 的指令)
> "请基于以上 PRD，在现有 Spring Boot 项目中实现 Day 2 的 RAG 功能。要求代码结构严谨，大量使用 JDK 17 特性（如 Records, Var, Standard Stream API），并确保所有方法都有详尽的中文注释。特别注意 LangChain4j DashScope 的配置方式，以及 PostgreSQL 向量检索的 Repository 实现。"