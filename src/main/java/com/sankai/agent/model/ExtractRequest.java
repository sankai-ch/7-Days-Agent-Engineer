package com.sankai.agent.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提取请求模型，封装用户输入的待处理文本。
 */
public class ExtractRequest {
    
    /**
     * 用户提供的原始文本内容。
     * 不能为空，且长度限制在 1 到 3000 个字符之间。
     * 支持 "user_text" 作为 JSON 字段别名。
     */
    @NotBlank
    @Size(min = 1, max = 3000)
    @JsonAlias("user_text")
    private String userText;

    /**
     * 获取用户文本内容。
     * 
     * @return 用户输入的文本
     */
    public String getUserText() {
        return userText;
    }

    /**
     * 设置用户文本内容。
     * 
     * @param userText 待处理的用户文本
     */
    public void setUserText(String userText) {
        this.userText = userText;
    }
}
