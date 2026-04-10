package com.sankai.agent.service;

import com.sankai.agent.model.Citation;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 检索服务类，负责从知识库中检索相关文档片段。
 */
@Service
public class RetrievalService {
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 构造函数，注入 KnowledgeBaseService。
     *
     * @param knowledgeBaseService 知识库服务实例
     */
    public RetrievalService(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * 根据问题检索最相关的文档片段。
     *
     * @param question 用户提问
     * @param topK     希望检索到的前 K 个结果
     * @return 包含相关文档片段的引用列表
     */
    public List<Citation> retrieveTop(String question, int topK) {
        // 将问题分词并转换为小写，用于匹配
        Set<String> queryTokens = tokenize(question);

        return knowledgeBaseService.allDocuments().stream()
                .map(doc -> {
                    // 将文档内容分词并转换为小写
                    Set<String> docTokens = tokenize(doc.content());
                    // 计算查询词和文档词的重叠数量
                    long overlap = queryTokens.stream().filter(docTokens::contains).count();
                    // 计算相似度分数，如果查询词为空则分数为 0
                    double score = queryTokens.isEmpty() ? 0.0 : (double) overlap / queryTokens.size();
                    // 截取文档内容作为片段，并替换换行符
                    String snippet = doc.content().length() > 220 ? doc.content().substring(0, 220) : doc.content();
                    return new Citation(doc.docId(), snippet.replace("\n", " "), score);
                })
                .filter(c -> c.getScore() > 0) // 过滤掉分数为 0 的结果
                .sorted(Comparator.comparingDouble(Citation::getScore).reversed()) // 按分数降序排序
                .limit(topK) // 限制返回结果的数量
                .toList();
    }

    /**
     * 对文本进行分词处理，转换为小写并去除空白。
     *
     * @param text 待分词的文本
     * @return 包含分词结果的 Set
     */
    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-zA-Z0-9\\u4e00-\\u9fa5]+"))
                .filter(t -> !t.isBlank())
                .collect(Collectors.toSet());
    }
}
