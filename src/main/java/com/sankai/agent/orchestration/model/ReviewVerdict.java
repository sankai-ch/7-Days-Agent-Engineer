package com.sankai.agent.orchestration.model;

import java.util.List;

/**
 * Reviewer 的复核裁决结果。
 *
 * <p>Reviewer 对 Executor 的全部执行结果进行质量评估后输出此对象：
 * <ul>
 *   <li>{@code PASS} — 所有步骤执行正确、数据充分，可以直接汇总</li>
 *   <li>{@code NEEDS_SUPPLEMENT} — 部分数据不足或存在疑点，建议补充执行</li>
 *   <li>{@code FAIL} — 执行结果不可用，需要重新规划</li>
 * </ul>
 */
public class ReviewVerdict {

    /** 裁决状态 */
    private Verdict verdict;

    /** Reviewer 的评价说明 */
    private String comment;

    /** 质量评分（0-100） */
    private int qualityScore;

    /** 需要补充执行的步骤（仅当 verdict = NEEDS_SUPPLEMENT 时有值） */
    private List<PlanStep> supplementSteps;

    public ReviewVerdict() {}

    // ==================== 静态工厂方法 ====================

    public static ReviewVerdict pass(String comment, int qualityScore) {
        ReviewVerdict v = new ReviewVerdict();
        v.verdict = Verdict.PASS;
        v.comment = comment;
        v.qualityScore = qualityScore;
        return v;
    }

    public static ReviewVerdict needsSupplement(String comment, int qualityScore,
                                                List<PlanStep> supplementSteps) {
        ReviewVerdict v = new ReviewVerdict();
        v.verdict = Verdict.NEEDS_SUPPLEMENT;
        v.comment = comment;
        v.qualityScore = qualityScore;
        v.supplementSteps = supplementSteps;
        return v;
    }

    public static ReviewVerdict fail(String comment) {
        ReviewVerdict v = new ReviewVerdict();
        v.verdict = Verdict.FAIL;
        v.comment = comment;
        v.qualityScore = 0;
        return v;
    }

    /** 是否通过复核 */
    public boolean isPassed() {
        return verdict == Verdict.PASS;
    }

    /** 是否需要补充 */
    public boolean needsSupplement() {
        return verdict == Verdict.NEEDS_SUPPLEMENT;
    }

    // ==================== 裁决枚举 ====================

    public enum Verdict {
        /** 通过——数据充分、执行正确 */
        PASS,
        /** 需要补充——部分数据不足 */
        NEEDS_SUPPLEMENT,
        /** 失败——结果不可用 */
        FAIL
    }

    // ==================== Getter / Setter ====================

    public Verdict getVerdict() { return verdict; }
    public void setVerdict(Verdict verdict) { this.verdict = verdict; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public int getQualityScore() { return qualityScore; }
    public void setQualityScore(int qualityScore) { this.qualityScore = qualityScore; }
    public List<PlanStep> getSupplementSteps() { return supplementSteps; }
    public void setSupplementSteps(List<PlanStep> supplementSteps) { this.supplementSteps = supplementSteps; }
}
