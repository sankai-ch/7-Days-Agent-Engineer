package com.sankai.agent.service;

/**
 * 知识文档记录类。
 *
 * @param docId   文档的唯一标识符
 * @param content 文档的详细内容
 */
public record KnowledgeDocument(String docId, String content) {
}
