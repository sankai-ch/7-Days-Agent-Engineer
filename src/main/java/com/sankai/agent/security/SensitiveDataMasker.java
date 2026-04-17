package com.sankai.agent.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 敏感信息脱敏器。
 *
 * <p>对 LLM 的输入和输出进行敏感信息脱敏，防止：
 * <ul>
 *   <li>用户无意中在输入里包含敏感信息被传给 LLM</li>
 *   <li>LLM 在回答中泄露数据库连接串、密码等配置</li>
 *   <li>工具返回结果中包含不应暴露的内部信息</li>
 * </ul>
 *
 * <h3>脱敏规则：</h3>
 * <ul>
 *   <li>手机号：138****1234</li>
 *   <li>身份证号：前 3 后 4 保留</li>
 *   <li>邮箱：u***@example.com</li>
 *   <li>密码/密钥：全部替换为 [MASKED]</li>
 *   <li>数据库连接串：jdbc:***</li>
 *   <li>IP 地址：内网 IP 脱敏</li>
 * </ul>
 */
@Component
public class SensitiveDataMasker {

    private static final Logger log = LoggerFactory.getLogger(SensitiveDataMasker.class);

    /** 脱敏规则列表：[正则, 替换模板, 描述] */
    private static final List<MaskRule> MASK_RULES = List.of(
            // 手机号（中国大陆 11 位）
            new MaskRule(
                    Pattern.compile("(?<=\\D|^)(1[3-9]\\d)(\\d{4})(\\d{4})(?=\\D|$)"),
                    "$1****$3",
                    "手机号"),

            // 身份证号（18 位）
            new MaskRule(
                    Pattern.compile("(?<=\\D|^)(\\d{3})\\d{11}(\\d{4})(?=\\D|$)"),
                    "$1***********$2",
                    "身份证号"),

            // 邮箱
            new MaskRule(
                    Pattern.compile("([a-zA-Z0-9])[a-zA-Z0-9.]*@([a-zA-Z0-9.-]+)"),
                    "$1***@$2",
                    "邮箱"),

            // 密码字段（key=value 格式）
            new MaskRule(
                    Pattern.compile("(?i)(password|passwd|secret|token|api[_-]?key)\\s*[:=]\\s*\\S+"),
                    "$1=[MASKED]",
                    "密码/密钥"),

            // JDBC 连接串
            new MaskRule(
                    Pattern.compile("jdbc:[a-zA-Z]+://[^\\s\"']+"),
                    "jdbc:[MASKED]",
                    "数据库连接串"),

            // 内网 IP（10.x / 172.16-31.x / 192.168.x）
            new MaskRule(
                    Pattern.compile("(?:10\\.\\d{1,3}|172\\.(?:1[6-9]|2\\d|3[01])|192\\.168)\\.\\d{1,3}\\.\\d{1,3}"),
                    "[INTERNAL_IP]",
                    "内网IP")
    );

    /**
     * 对文本进行脱敏处理。
     *
     * @param text 原始文本
     * @return 脱敏后的文本
     */
    public String mask(String text) {
        if (text == null || text.isEmpty()) return text;

        String masked = text;
        for (MaskRule rule : MASK_RULES) {
            String before = masked;
            masked = rule.pattern.matcher(masked).replaceAll(rule.replacement);
            if (!before.equals(masked)) {
                log.debug("[脱敏] 命中规则: {}", rule.description);
            }
        }
        return masked;
    }

    /**
     * 检查文本中是否包含敏感信息。
     *
     * @param text 待检查文本
     * @return true 如果包含敏感信息
     */
    public boolean containsSensitive(String text) {
        if (text == null) return false;
        return MASK_RULES.stream().anyMatch(rule -> rule.pattern.matcher(text).find());
    }

    /** 脱敏规则内部类 */
    private record MaskRule(Pattern pattern, String replacement, String description) {}
}
