package com.sankai.agent;

import com.sankai.agent.repository.SegmentRepository;
import com.sankai.agent.service.KnowledgeBaseService;
import com.sankai.agent.service.RetrievalService;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
public class TestBeans {

    @Bean
    @Primary
    public QwenEmbeddingModel embeddingModel() {
        var mock = mock(QwenEmbeddingModel.class);
        when(mock.embed(any(TextSegment.class))).thenReturn(Response.from(Embedding.from(new float[1024])));
        return mock;
    }

    @Bean
    @Primary
    public QwenChatModel chatModel() {
        var mock = mock(QwenChatModel.class);
        when(mock.chat(any(List.class))).thenReturn(
                ChatResponse.builder().aiMessage(AiMessage.from("测试回答，来源：mcp-agent.md")).build());
        return mock;
    }

    @Bean
    @Primary
    public SegmentRepository segmentRepository() {
        var mock = mock(SegmentRepository.class);
        when(mock.findTopK(any(), any(Integer.class))).thenReturn(
                List.of(new SegmentRepository.SegmentRow("MCP是工具协议", "{\"docId\":\"mcp-agent.md\"}", 0.9)));
        return mock;
    }

    @Bean
    @Primary
    public KnowledgeBaseService knowledgeBaseService(QwenEmbeddingModel em, SegmentRepository repo) {
        return new KnowledgeBaseService(em, repo);
    }

    @Bean
    @Primary
    public RetrievalService retrievalService(QwenEmbeddingModel em, SegmentRepository repo) {
        return new RetrievalService(em, repo);
    }
}
