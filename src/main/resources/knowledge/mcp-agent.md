MCP（Model Context Protocol）用于让 Agent 统一连接外部工具与数据源。
在 Java 项目中，可以把 MCP 工具调用封装成 service 层，并加入鉴权与审计。
实践中建议先从 2-3 个高价值工具开始，例如查询工单、查询日志、查询发布记录。
