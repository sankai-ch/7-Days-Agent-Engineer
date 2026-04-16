package com.sankai.agent.mcp.server;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * MCP Server 鉴权过滤器。
 *
 * <p>对所有访问 MCP Server 端点（{@code /mcp/**}）的请求进行 Token 鉴权。
 * 采用 Bearer Token 方式验证，Token 配置在 application.yml 中。
 *
 * <h3>鉴权流程：</h3>
 * <ol>
 *   <li>仅拦截 {@code /mcp/} 开头的请求路径</li>
 *   <li>从 HTTP Header {@code Authorization} 中提取 Bearer Token</li>
 *   <li>与配置文件中的 {@code mcp.server.auth-token} 进行比对</li>
 *   <li>匹配成功放行，失败返回 401 Unauthorized</li>
 * </ol>
 *
 * <h3>配置示例（application.yml）：</h3>
 * <pre>{@code
 * mcp:
 *   server:
 *     auth-token: my-secret-token-12345
 * }</pre>
 *
 * <h3>请求示例：</h3>
 * <pre>{@code
 * POST /mcp/rpc
 * Authorization: Bearer my-secret-token-12345
 * Content-Type: application/json
 * }</pre>
 *
 * <p>安全注意事项：
 * <ul>
 *   <li>生产环境中应使用更复杂的鉴权机制（如 JWT、OAuth2）</li>
 *   <li>Token 不应硬编码在代码中，应通过环境变量或密钥管理服务注入</li>
 *   <li>建议配合 HTTPS 使用，防止 Token 在传输过程中被窃取</li>
 * </ul>
 */
@Component
@Order(1) // 确保此过滤器优先执行
public class McpAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpAuthFilter.class);

    /** MCP 端点路径前缀 */
    private static final String MCP_PATH_PREFIX = "/mcp/";

    /** Bearer Token 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 从配置文件读取鉴权 Token。
     * 如果未配置（为空），则跳过鉴权（方便开发调试）。
     */
    @Value("${mcp.server.auth-token:}")
    private String authToken;

    /**
     * 只对 MCP 路径下的请求进行过滤。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(MCP_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 如果未配置 auth-token，跳过鉴权（开发模式）
        if (authToken == null || authToken.isBlank()) {
            log.debug("[MCP Auth] 未配置 auth-token，跳过鉴权");
            filterChain.doFilter(request, response);
            return;
        }

        // 从 Authorization Header 中提取 Token
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("[MCP Auth] 缺少 Authorization Header 或格式错误，来源 IP: {}",
                    request.getRemoteAddr());
            sendUnauthorized(response, "缺少有效的 Authorization Header");
            return;
        }

        // 提取并校验 Token
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (!authToken.equals(token)) {
            log.warn("[MCP Auth] Token 校验失败，来源 IP: {}", request.getRemoteAddr());
            sendUnauthorized(response, "无效的认证 Token");
            return;
        }

        log.debug("[MCP Auth] 鉴权通过，请求路径: {}", request.getRequestURI());
        filterChain.doFilter(request, response);
    }

    /**
     * 返回 401 未授权响应。
     *
     * @param response HTTP 响应对象
     * @param message  错误提示信息
     */
    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32000,\"message\":\"" + message + "\"}}");
    }
}
