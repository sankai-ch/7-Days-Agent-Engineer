package com.sankai.agent.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Token 用量估算与成本计算器。
 *
 * <p>由于通义千问 API 返回中并不总是携带 token 用量信息，
 * 这里使用基于字符数的估算方式（中文约 1.5 字 ≈ 1 token，英文约 4 字符 ≈ 1 token）。
 *
 * <h3>定价参考（通义千问 qwen-plus）：</h3>
 * <ul>
 *   <li>输入：0.0008 元 / 千 token</li>
 *   <li>输出：0.002 元 / 千 token</li>
 * </ul>
 *
 * <p>配置在 application.yml 中，可按模型调整。
 */
@Component
public class CostTracker {

    /** 输入价格：元 / 千 token */
    @Value("${observability.cost.input-price-per-1k:0.0008}")
    private double inputPricePer1k;

    /** 输出价格：元 / 千 token */
    @Value("${observability.cost.output-price-per-1k:0.002}")
    private double outputPricePer1k;

    /**
     * 估算文本的 token 数量。
     *
     * <p>估算规则：
     * <ul>
     *   <li>中文字符：1 字 ≈ 0.7 token</li>
     *   <li>英文/数字：4 字符 ≈ 1 token</li>
     *   <li>混合文本取加权平均</li>
     * </ul>
     *
     * @param text 文本内容
     * @return 估算 token 数
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;

        int chineseCount = 0;
        int otherCount = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseCount++;
            } else {
                otherCount++;
            }
        }
        // 中文 1 字 ≈ 0.7 token，其他字符 4 字符 ≈ 1 token
        return (int) (chineseCount * 0.7 + otherCount / 4.0) + 1;
    }

    /**
     * 估算一次 LLM 调用的总 token（输入 + 输出）。
     */
    public int estimateCallTokens(String input, String output) {
        return estimateTokens(input) + estimateTokens(output);
    }

    /**
     * 根据 token 数估算成本（元）。
     *
     * @param inputTokens  输入 token 数
     * @param outputTokens 输出 token 数
     * @return 成本（元）
     */
    public double estimateCost(int inputTokens, int outputTokens) {
        return inputTokens / 1000.0 * inputPricePer1k
                + outputTokens / 1000.0 * outputPricePer1k;
    }

    /**
     * 便捷方法：从文本直接估算总 token 和成本。
     *
     * @param inputText  输入文本
     * @param outputText 输出文本
     * @return [tokenEstimate, costEstimate]
     */
    public double[] estimate(String inputText, String outputText) {
        int inputTokens = estimateTokens(inputText);
        int outputTokens = estimateTokens(outputText);
        double cost = estimateCost(inputTokens, outputTokens);
        return new double[]{inputTokens + outputTokens, cost};
    }

    // ==================== Getter（供测试/调试） ====================

    public double getInputPricePer1k() { return inputPricePer1k; }
    public double getOutputPricePer1k() { return outputPricePer1k; }
}
