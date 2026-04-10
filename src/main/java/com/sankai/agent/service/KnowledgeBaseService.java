package com.sankai.agent.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库服务类。
 * 负责从指定资源路径加载知识文档。
 */
@Service
public class KnowledgeBaseService {
    private final List<KnowledgeDocument> documents;

    /**
     * 构造函数，初始化并加载文档。
     */
    public KnowledgeBaseService() {
        this.documents = loadDocuments();
    }

    /**
     * 返回所有已加载的文档。
     *
     * @return 知识文档列表
     */
    public List<KnowledgeDocument> allDocuments() {
        return documents;
    }

    /**
     * 从类路径下的 knowledge/*.md 加载文档。
     *
     * @return 解析后的文档列表
     */
    private List<KnowledgeDocument> loadDocuments() {
        List<KnowledgeDocument> docs = new ArrayList<>();
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            // 加载所有以 .md 结尾的资源文件
            Resource[] resources = resolver.getResources("classpath:knowledge/*.md");
            for (Resource resource : resources) {
                // 获取文件名作为文档 ID，内容读入并以 UTF-8 编码
                String filename = resource.getFilename() == null ? "unknown" : resource.getFilename();
                String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                docs.add(new KnowledgeDocument(filename, content));
            }
        } catch (Exception ex) {
            // 如果加载失败则抛出非法状态异常
            throw new IllegalStateException("Failed to load knowledge documents", ex);
        }
        return docs;
    }
}
