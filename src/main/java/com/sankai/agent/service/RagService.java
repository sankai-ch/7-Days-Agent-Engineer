package com.sankai.agent.service;

import com.sankai.agent.model.AskResponse;
import com.sankai.agent.model.Citation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 检索增强生成 (RAG) 服务类。
 * 结合检索到的知识片段生成最终答案。
 */
@Service
public class RagService {
    private final RetrievalService retrievalService;

    /**
     * 构造函数。
     *
     * @param retrievalService 用于检索知识的服务
     */
    public RagService(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    /**
     * 处理提问逻辑，通过检索增强生成答案。
     *
     * @param question 用户提问的内容
     * @return 封装了答案、事实依据标志和引用信息的响应对象
     */
    public AskResponse ask(String question) {
        // 检索最相关的前 3 个片段
        List<Citation> citations = retrievalService.retrieveTop(question, 3);
        
        // 如果未检索到任何内容，返回提示信息
        if (citations.isEmpty()) {
            return new AskResponse(
                    "我在当前知识库里没有找到足够依据。请补充更具体的问题或先扩展知识库文档。",
                    false,
                    List.of()
            );
        }

        // 将检索到的片段拼接成摘要
        String summary = citations.stream()
                .map(c -> "[" + c.getDocId() + "] " + c.getSnippet())
                .collect(Collectors.joining(" "));

        // 生成最终答案
        String answer = "基于检索结果，我建议先按以下线索处理：" + summary;
        return new AskResponse(answer, true, citations);
    }
}
