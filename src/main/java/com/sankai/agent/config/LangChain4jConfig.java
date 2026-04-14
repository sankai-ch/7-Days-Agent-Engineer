package com.sankai.agent.config;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangChain4jConfig {

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${dashscope.embedding-model:text-embedding-v3}")
    private String embeddingModel;

    @Value("${dashscope.chat-model:qwen-plus}")
    private String chatModel;

    @Bean
    public QwenEmbeddingModel embeddingModel() {
        return QwenEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(embeddingModel)
                .build();
    }

    @Bean
    public QwenChatModel chatModel() {
        return QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName(chatModel)
                .build();
    }
}
