package com.sankai.agent.service;

import com.sankai.agent.client.LlmClient;
import com.sankai.agent.exception.ExtractionException;
import com.sankai.agent.model.ExtractResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 提取服务类，负责调用 LLM 并解析/校验结果。
 */
@Service
public class ExtractionService {
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    /**
     * 构造函数，注入必要的依赖。
     *
     * @param llmClient    用于与大模型通信的客户端
     * @param objectMapper 用于 JSON 序列化和反序列化
     * @param validator    用于校验提取结果的合法性
     */
    public ExtractionService(LlmClient llmClient, ObjectMapper objectMapper, Validator validator) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /**
     * 从用户文本中提取结构化数据，包含重试机制。
     *
     * @param userText 用户的原始输入文本
     * @return 成功提取并校验后的结构化结果
     * @throws ExtractionException 如果多次尝试后仍然失败，抛出提取异常
     */
    public ExtractResult extract(String userText) {
        int maxRetries = 3;
        Exception lastError = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String prompt = buildPrompt(userText);
                String raw = llmClient.complete(prompt);
                ExtractResult result = objectMapper.readValue(raw, ExtractResult.class);
                validateResult(result);
                return result;
            } catch (Exception ex) {
                lastError = ex;
                if (attempt < maxRetries) {
                    sleep(150L * attempt);
                }
            }
        }

        throw new ExtractionException("Failed after 3 attempts", lastError);
    }

    /**
     * 构建发送给 LLM 的提示词。
     *
     * @param userText 用户输入内容
     * @return 格式化后的提示词字符串
     */
    private String buildPrompt(String userText) {
        return "You are an extraction engine. Return strict JSON with keys: " +
                "intent, priority, summary, tags, needsFollowUp, confidence. " +
                "Input: " + userText;
    }

    /**
     * 对提取出的结果进行校验。
     *
     * @param result 提取出的模型对象
     * @throws IllegalArgumentException 如果校验不通过，抛出非法参数异常
     */
    private void validateResult(ExtractResult result) {
        if (result.getSummary() == null || result.getSummary().isBlank()) {
            throw new IllegalArgumentException("summary is required");
        }
        if (result.getConfidence() < 0.0 || result.getConfidence() > 1.0) {
            throw new IllegalArgumentException("confidence out of range");
        }

        Set<ConstraintViolation<ExtractResult>> violations = validator.validate(result);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("result validation failed");
        }
    }

    /**
     * 线程休眠，用于重试时的退避策略。
     *
     * @param millis 休眠毫秒数
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Sleep interrupted", e);
        }
    }
}
