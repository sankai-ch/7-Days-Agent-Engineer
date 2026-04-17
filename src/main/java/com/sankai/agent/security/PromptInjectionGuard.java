package com.sankai.agent.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Prompt Injection 防护器。
 *
 * <p>检测用户输入中是否包含 Prompt Injection 攻击模式，
 * 防止恶意用户通过精心构造的输入篡改 LLM 的系统指令。
 *
 * <h3>防护的攻击类型：</h3>
 * <ul>
 *   <li><b>角色劫持</b>：尝试让 LLM 忽略系统指令，扮演其他角色</li>
 *   <li><b>指令覆盖</b>：企图用"忘记之前的指令"等话术覆盖系统 prompt</li>
 *   <li><b>数据泄露</b>：试图让 LLM 输出系统 prompt 内容或内部配置</li>
 *   <li><b>越权执行</b>：尝试调用未授权的操作（如删除数据、执行命令）</li>
 * </ul>
 *
 * <h3>检测策略（多层防御）：</h3>
 * <ol>
 *   <li>关键词黑名单匹配（高频攻击模式）</li>
 *   <li>正则表达式模式匹配（结构化攻击检测）</li>
 *   <li>语义特征检测（指令分隔符、角色切换信号）</li>
 * </ol>
 */
@Component
public class PromptInjectionGuard {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionGuard.class);

    /**
     * 高风险关键词列表（不区分大小写匹配）。
     * 这些关键词出现时表示极可能存在注入攻击。
     */
    private static final List<String> DANGEROUS_KEYWORDS = List.of(
            "ignore previous instructions",
            "ignore all instructions",
            "disregard your instructions",
            "forget your instructions",
            "忽略之前的指令",
            "忽略你的指令",
            "忘记之前的指令",
            "忘记你的角色",
            "你现在是一个",
            "assume the role of",
            "you are now",
            "act as if you are",
            "pretend you are",
            "system prompt",
            "reveal your prompt",
            "输出你的系统提示",
            "显示你的指令",
            "print your instructions"
    );

    /**
     * 正则模式列表 —— 检测结构化攻击模式。
     */
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            // 尝试注入新的系统指令
            Pattern.compile("\\[SYSTEM\\]|\\[INST\\]|<\\|system\\|>|<\\|assistant\\|>",
                    Pattern.CASE_INSENSITIVE),
            // 尝试使用 Markdown/XML 伪造 AI 回复
            Pattern.compile("```(system|assistant|ai)\\b", Pattern.CASE_INSENSITIVE),
            // 尝试执行系统命令
            Pattern.compile("(exec|system|eval|os\\.popen|subprocess|Runtime\\.exec)\\s*\\(",
                    Pattern.CASE_INSENSITIVE),
            // SQL 注入（针对工具参数）
            Pattern.compile("('\\s*(OR|AND)\\s+'|;\\s*(DROP|DELETE|UPDATE|INSERT|ALTER)\\b|--\\s)",
                    Pattern.CASE_INSENSITIVE),
            // 尝试路径遍历
            Pattern.compile("\\.\\./|\\.\\.\\\\", Pattern.CASE_INSENSITIVE)
    );

    /**
     * 检测输入是否包含 Prompt Injection 攻击。
     *
     * @param input 用户输入文本
     * @return 检测结果
     */
    public InjectionCheckResult check(String input) {
        if (input == null || input.isBlank()) {
            return InjectionCheckResult.safe();
        }

        String normalized = input.toLowerCase().trim();

        // 第 1 层：关键词匹配
        for (String keyword : DANGEROUS_KEYWORDS) {
            if (normalized.contains(keyword.toLowerCase())) {
                log.warn("[安全] Prompt Injection 检测命中关键词: '{}'", keyword);
                return InjectionCheckResult.blocked(
                        "KEYWORD_MATCH",
                        "检测到危险指令关键词: " + keyword);
            }
        }

        // 第 2 层：正则模式匹配
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.warn("[安全] Prompt Injection 检测命中模式: {}", pattern.pattern());
                return InjectionCheckResult.blocked(
                        "PATTERN_MATCH",
                        "检测到危险输入模式");
            }
        }

        // 第 3 层：异常长度检测（超长输入可能是 jailbreak 攻击）
        if (input.length() > 5000) {
            log.warn("[安全] 输入长度异常: {} 字符", input.length());
            return InjectionCheckResult.blocked(
                    "LENGTH_EXCEEDED",
                    "输入长度超过安全限制 (最大 5000 字符)");
        }

        return InjectionCheckResult.safe();
    }

    /**
     * 注入检测结果。
     */
    public static class InjectionCheckResult {
        private final boolean safe;
        private final String ruleId;
        private final String reason;

        private InjectionCheckResult(boolean safe, String ruleId, String reason) {
            this.safe = safe;
            this.ruleId = ruleId;
            this.reason = reason;
        }

        public static InjectionCheckResult safe() {
            return new InjectionCheckResult(true, null, null);
        }

        public static InjectionCheckResult blocked(String ruleId, String reason) {
            return new InjectionCheckResult(false, ruleId, reason);
        }

        public boolean isSafe() { return safe; }
        public String getRuleId() { return ruleId; }
        public String getReason() { return reason; }
    }
}
