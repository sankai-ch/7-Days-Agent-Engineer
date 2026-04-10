package com.sankai.agent.client;

/**
 * LLM 客户端接口，定义与语言模型交互的标准方法。
 */
public interface LlmClient {
    /**
     * 根据给定的提示词生成补全内容。
     *
     * @param prompt 提示词字符串
     * @return 模型返回的文本结果
     */
    String complete(String prompt);
}
