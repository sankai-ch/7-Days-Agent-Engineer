package com.sankai.agent.model;

/**
 * 表示引用的实体类。
 */
public class Citation {
    /** 文档标识符 */
    private String docId;
    /** 文档片段 */
    private String snippet;
    /** 匹配分数 */
    private double score;

    /**
     * 无参构造函数。
     */
    public Citation() {
    }

    /**
     * 带参数的构造函数。
     *
     * @param docId   文档 ID
     * @param snippet 文档片段
     * @param score   匹配分值
     */
    public Citation(String docId, String snippet, double score) {
        this.docId = docId;
        this.snippet = snippet;
        this.score = score;
    }

    /**
     * 获取文档 ID。
     *
     * @return 文档 ID
     */
    public String getDocId() {
        return docId;
    }

    /**
     * 设置文档 ID。
     *
     * @param docId 文档 ID
     */
    public void setDocId(String docId) {
        this.docId = docId;
    }

    /**
     * 获取文档片段。
     *
     * @return 文档片段
     */
    public String getSnippet() {
        return snippet;
    }

    /**
     * 设置文档片段。
     *
     * @param snippet 文档片段
     */
    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    /**
     * 获取匹配分数。
     *
     * @return 匹配分值
     */
    public double getScore() {
        return score;
    }

    /**
     * 设置匹配分数。
     *
     * @param score 匹配分值
     */
    public void setScore(double score) {
        this.score = score;
    }
}
