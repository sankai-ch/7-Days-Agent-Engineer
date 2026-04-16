-- =====================================================
-- Day 1 & 2：知识库向量表（RAG 基础）
-- =====================================================
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS knowledge_segments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content     TEXT NOT NULL,                           -- 文档切片内容
    metadata    JSONB,                                   -- 元数据（docId 等）
    embedding   vector(1024),                            -- 文本向量（1024 维）
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS knowledge_segments_embedding_idx
    ON knowledge_segments USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- =====================================================
-- Day 3：MCP 工具所需的数据表
-- =====================================================

-- ----- 应用日志表（供 query_logs 工具查询） -----
CREATE TABLE IF NOT EXISTS app_logs (
    id          BIGSERIAL PRIMARY KEY,
    level       VARCHAR(10) NOT NULL,                    -- 日志级别：INFO / WARN / ERROR
    service     VARCHAR(100) NOT NULL,                   -- 服务名称
    message     TEXT NOT NULL,                            -- 日志消息
    trace_id    VARCHAR(64),                             -- 链路追踪 ID
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 为日志查询常用字段添加索引
CREATE INDEX IF NOT EXISTS idx_app_logs_level ON app_logs (level);
CREATE INDEX IF NOT EXISTS idx_app_logs_service ON app_logs (service);
CREATE INDEX IF NOT EXISTS idx_app_logs_created_at ON app_logs (created_at DESC);

-- ----- 工单表（供 query_tickets 工具查询） -----
CREATE TABLE IF NOT EXISTS work_tickets (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200) NOT NULL,                   -- 工单标题
    description TEXT,                                     -- 工单描述
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',     -- 状态：OPEN / IN_PROGRESS / RESOLVED / CLOSED
    priority    VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',   -- 优先级：LOW / MEDIUM / HIGH / CRITICAL
    assignee    VARCHAR(100),                             -- 负责人
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 为工单查询常用字段添加索引
CREATE INDEX IF NOT EXISTS idx_work_tickets_status ON work_tickets (status);
CREATE INDEX IF NOT EXISTS idx_work_tickets_priority ON work_tickets (priority);
CREATE INDEX IF NOT EXISTS idx_work_tickets_assignee ON work_tickets (assignee);

-- =====================================================
-- 示例数据：应用日志
-- =====================================================
INSERT INTO app_logs (level, service, message, trace_id) VALUES
    ('INFO',  'user-service',   '用户 zhangsan 登录成功',                          'trace-001'),
    ('INFO',  'user-service',   '用户 lisi 修改了个人资料',                        'trace-002'),
    ('WARN',  'order-service',  '订单 ORD-2024-001 库存不足，触发预警',            'trace-003'),
    ('ERROR', 'order-service',  '订单 ORD-2024-002 支付回调超时，重试 3 次仍失败', 'trace-004'),
    ('ERROR', 'gateway',        '上游服务 product-service 连接被拒绝 (Connection refused)', 'trace-005'),
    ('INFO',  'product-service','商品 SKU-10086 上架成功，价格 299.00',            'trace-006'),
    ('WARN',  'gateway',        'API /v1/orders 请求耗时 3200ms，超过阈值',       'trace-007'),
    ('ERROR', 'user-service',   'Redis 连接池耗尽，当前活跃连接 50/50',           'trace-008'),
    ('INFO',  'order-service',  '日终批量结算完成，共处理 1286 笔订单',            'trace-009'),
    ('WARN',  'product-service','商品图片 CDN 回源率升至 35%，建议检查缓存策略',   'trace-010')
ON CONFLICT DO NOTHING;

-- =====================================================
-- 示例数据：工单
-- =====================================================
INSERT INTO work_tickets (title, description, status, priority, assignee) VALUES
    ('登录页面偶发 502 错误',
     '用户反馈在高峰期（10:00-11:00）登录时偶尔出现 502 Bad Gateway，约 5% 请求受影响。初步排查发现 Nginx upstream 超时。',
     'OPEN', 'HIGH', '张三'),

    ('订单支付超时重试机制优化',
     '当前支付回调超时后的重试策略为固定间隔 5s，建议改为指数退避（1s, 2s, 4s），减少第三方支付网关压力。',
     'IN_PROGRESS', 'HIGH', '李四'),

    ('用户头像上传功能失效',
     '部分安卓用户反馈上传头像后显示空白图片。经排查为 WebP 格式兼容性问题，需要在服务端增加格式转换。',
     'OPEN', 'MEDIUM', '王五'),

    ('数据库慢查询优化',
     'order_items 表全表扫描导致 /v1/orders 接口 P99 响应时间达 4.2s。需要添加组合索引 (order_id, created_at)。',
     'RESOLVED', 'HIGH', '张三'),

    ('更新用户协议页面文案',
     '法务部门要求更新隐私政策第 3.2 节关于数据共享的说明，截止日期 2024-12-31。',
     'OPEN', 'LOW', '赵六'),

    ('商品搜索结果排序异常',
     '按价格升序排列时，部分商品顺序错误。原因是 price 字段存在 NULL 值未处理。',
     'IN_PROGRESS', 'MEDIUM', '李四'),

    ('接入钉钉机器人告警通知',
     '将 ERROR 级别日志和 P0/P1 工单自动推送到运维钉钉群，要求 5 分钟内触达。',
     'OPEN', 'MEDIUM', '王五'),

    ('Redis 连接池参数调优',
     '生产环境 Redis 连接池频繁耗尽（maxTotal=50），建议调整为 maxTotal=200, maxIdle=50, minIdle=10。',
     'OPEN', 'CRITICAL', '张三')
ON CONFLICT DO NOTHING;
