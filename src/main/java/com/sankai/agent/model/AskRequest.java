package com.sankai.agent.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提问请求实体类。
 */
public class AskRequest {
    /** 用户提问的内容 */
    @NotBlank
    @Size(min = 3, max = 1000)
    private String question;

    /**
     * 获取提问的问题。
     *
     * @return 问题内容
     */
    public String getQuestion() {
        return question;
    }

    /**
     * 设置提问的问题。
     *
     * @param question 问题内容
     */
    public void setQuestion(String question) {
        this.question = question;
    }
}
