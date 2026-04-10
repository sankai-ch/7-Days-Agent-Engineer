package com.sankai.agent.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * 提取结果模型，表示从文本中解析出的结构化信息。
 */
public class ExtractResult {
    
    /**
     * 用户的意图类型。
     */
    @NotNull
    private Intent intent;
    
    /**
     * 该请求的优先级。
     */
    @NotNull
    private Priority priority;
    
    /**
     * 对用户文本的简短摘要（最多 200 字）。
     */
    @NotBlank
    @Size(max = 200)
    private String summary;
    
    /**
     * 提取出的标签列表（最多 10 个）。
     */
    @NotNull
    @Size(max = 10)
    private List<String> tags = new ArrayList<>();
    
    /**
     * 是否需要后续跟进。
     */
    private boolean needsFollowUp;
    
    /**
     * 提取结果的置信度（0.0 到 1.0 之间）。
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double confidence;

    /**
     * 获取用户意图。
     *
     * @return 意图类型
     */
    public Intent getIntent() {
        return intent;
    }

    /**
     * 设置用户意图。
     *
     * @param intent 意图类型
     */
    public void setIntent(Intent intent) {
        this.intent = intent;
    }

    /**
     * 获取优先级。
     *
     * @return 优先级
     */
    public Priority getPriority() {
        return priority;
    }

    /**
     * 设置优先级。
     *
     * @param priority 优先级
     */
    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    /**
     * 获取摘要。
     *
     * @return 摘要内容
     */
    public String getSummary() {
        return summary;
    }

    /**
     * 设置摘要。
     *
     * @param summary 摘要内容
     */
    public void setSummary(String summary) {
        this.summary = summary;
    }

    /**
     * 获取标签列表。
     *
     * @return 标签列表
     */
    public List<String> getTags() {
        return tags;
    }

    /**
     * 设置标签列表。
     *
     * @param tags 标签列表
     */
    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    /**
     * 检查是否需要后续跟进。
     *
     * @return true 如果需要后续跟进，否则 false
     */
    public boolean isNeedsFollowUp() {
        return needsFollowUp;
    }

    /**
     * 设置是否需要后续跟进。
     *
     * @param needsFollowUp 是否需要后续跟进
     */
    public void setNeedsFollowUp(boolean needsFollowUp) {
        this.needsFollowUp = needsFollowUp;
    }

    /**
     * 获取置信度。
     *
     * @return 置信度
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * 设置置信度。
     *
     * @param confidence 置信度
     */
    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    /**
     * 意图枚举。
     */
    public enum Intent {
        /** 问题 */
        question,
        /** 任务 */
        task,
        /** 反馈 */
        feedback,
        /** 其他 */
        other
    }

    /**
     * 优先级枚举。
     */
    public enum Priority {
        /** 低 */
        low,
        /** 中 */
        medium,
        /** 高 */
        high
    }
}
