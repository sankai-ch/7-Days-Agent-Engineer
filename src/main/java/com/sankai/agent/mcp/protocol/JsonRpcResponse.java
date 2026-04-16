package com.sankai.agent.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 响应模型。
 *
 * <p>MCP Server 对每个请求返回一个标准的 JSON-RPC 响应：
 * <ul>
 *   <li>成功时：包含 {@code result} 字段</li>
 *   <li>失败时：包含 {@code error} 字段</li>
 * </ul>
 *
 * <p>成功响应示例：
 * <pre>{@code
 * { "jsonrpc": "2.0", "id": 1, "result": { ... } }
 * }</pre>
 *
 * <p>错误响应示例：
 * <pre>{@code
 * { "jsonrpc": "2.0", "id": 1, "error": { "code": -32601, "message": "Method not found" } }
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // 只序列化非空字段
public class JsonRpcResponse {

    /** JSON-RPC 协议版本 */
    private String jsonrpc = "2.0";

    /** 对应请求的 ID，必须与请求中的 ID 一致 */
    private JsonNode id;

    /** 成功时的结果数据 */
    private Object result;

    /** 失败时的错误信息 */
    private ErrorObject error;

    // ==================== 静态工厂方法 ====================

    /**
     * 构建一个成功的 JSON-RPC 响应。
     *
     * @param id     对应请求的 ID
     * @param result 结果数据
     * @return 成功响应实例
     */
    public static JsonRpcResponse success(JsonNode id, Object result) {
        JsonRpcResponse resp = new JsonRpcResponse();
        resp.setId(id);
        resp.setResult(result);
        return resp;
    }

    /**
     * 构建一个错误的 JSON-RPC 响应。
     *
     * @param id      对应请求的 ID
     * @param code    错误码（参考 JSON-RPC 标准错误码）
     * @param message 错误描述
     * @return 错误响应实例
     */
    public static JsonRpcResponse error(JsonNode id, int code, String message) {
        JsonRpcResponse resp = new JsonRpcResponse();
        resp.setId(id);
        resp.setError(new ErrorObject(code, message));
        return resp;
    }

    // ==================== JSON-RPC 标准错误码常量 ====================

    /** 解析错误：无效的 JSON */
    public static final int PARSE_ERROR = -32700;
    /** 无效的请求 */
    public static final int INVALID_REQUEST = -32600;
    /** 方法不存在 */
    public static final int METHOD_NOT_FOUND = -32601;
    /** 参数无效 */
    public static final int INVALID_PARAMS = -32602;
    /** 内部错误 */
    public static final int INTERNAL_ERROR = -32603;

    // ==================== 错误对象内部类 ====================

    /**
     * JSON-RPC 错误对象。
     */
    public static class ErrorObject {
        private int code;
        private String message;

        public ErrorObject() {}

        public ErrorObject(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    // ==================== Getter / Setter ====================

    public String getJsonrpc() { return jsonrpc; }
    public void setJsonrpc(String jsonrpc) { this.jsonrpc = jsonrpc; }
    public JsonNode getId() { return id; }
    public void setId(JsonNode id) { this.id = id; }
    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
    public ErrorObject getError() { return error; }
    public void setError(ErrorObject error) { this.error = error; }
}
