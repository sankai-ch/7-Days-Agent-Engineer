package com.sankai.agent.client;

import org.springframework.stereotype.Component;

/**
 * 基于规则的 Mock 客户端，模拟大语言模型返回结构化 JSON。
 * 用于在没有实际大模型接入时进行功能测试。
 */
@Component
public class RuleBasedMockClient implements LlmClient {
    
    /**
     * 根据提示词中的关键词简单模拟意图识别和数据提取。
     *
     * @param prompt 提示词字符串
     * @return 模拟的 JSON 格式结果
     */
    @Override
    public String complete(String prompt) {
        String text = prompt.toLowerCase();

        String intent = "other";
        String priority = "low";

        // 简单的关键词匹配逻辑
        if (text.contains("?") || text.contains("how")) {
            intent = "question";
            priority = "medium";
        } else if (text.contains("urgent") || text.contains("asap")) {
            intent = "task";
            priority = "high";
        } else if (text.contains("thanks") || text.contains("great")) {
            intent = "feedback";
            priority = "low";
        }

        boolean needsFollowUp = "question".equals(intent) || "task".equals(intent);

        // 返回模拟生成的结构化 JSON 字符串
        return "{" +
                "\"intent\":\"" + intent + "\"," +
                "\"priority\":\"" + priority + "\"," +
                "\"summary\":\"Auto summary from mock client.\"," +
                "\"tags\":[\"demo\",\"day1\"]," +
                "\"needsFollowUp\":" + needsFollowUp + "," +
                "\"confidence\":0.82" +
                "}";
    }
}
