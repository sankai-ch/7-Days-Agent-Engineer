package com.sankai.agent.evaluation.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量评测报告。
 *
 * <p>汇总所有评测用例的执行结果，生成成功率、时延、成本等统计。
 */
public class EvalReport {

    /** 报告生成时间 */
    private Instant timestamp = Instant.now();

    /** 总用例数 */
    private int totalCases;

    /** 通过数 */
    private int passedCases;

    /** 失败数 */
    private int failedCases;

    /** 通过率 */
    private String passRate;

    /** 平均耗时 (ms) */
    private long avgDurationMs;

    /** 总 Token 估算 */
    private int totalTokenEstimate;

    /** 总成本估算 */
    private String totalCostEstimate;

    /** 按分类统计 */
    private Map<String, CategoryStats> categoryStats = new LinkedHashMap<>();

    /** 每条用例的结果 */
    private List<EvalResult> results = new ArrayList<>();

    /** 失败用例摘要 */
    private List<Map<String, Object>> failures = new ArrayList<>();

    /**
     * 从评测结果列表生成报告。
     */
    public static EvalReport fromResults(List<EvalResult> results) {
        EvalReport report = new EvalReport();
        report.results = results;
        report.totalCases = results.size();
        report.passedCases = (int) results.stream().filter(EvalResult::isPassed).count();
        report.failedCases = report.totalCases - report.passedCases;
        report.passRate = report.totalCases > 0
                ? String.format("%.1f%%", (double) report.passedCases / report.totalCases * 100)
                : "N/A";
        report.avgDurationMs = report.totalCases > 0
                ? (long) results.stream().mapToLong(EvalResult::getDurationMs).average().orElse(0)
                : 0;
        report.totalTokenEstimate = results.stream().mapToInt(EvalResult::getTokenEstimate).sum();
        double totalCost = results.stream().mapToDouble(EvalResult::getCostEstimate).sum();
        report.totalCostEstimate = String.format("¥%.4f", totalCost);

        // 失败摘要
        results.stream().filter(r -> !r.isPassed()).forEach(r -> {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("caseId", r.getCaseId());
            f.put("input", r.getInput());
            f.put("checks", r.getCheckDetails());
            if (r.getErrorMessage() != null) f.put("error", r.getErrorMessage());
            report.failures.add(f);
        });

        return report;
    }

    /** 分类统计 */
    public static class CategoryStats {
        private int total;
        private int passed;
        private String passRate;

        public CategoryStats(int total, int passed) {
            this.total = total;
            this.passed = passed;
            this.passRate = total > 0
                    ? String.format("%.1f%%", (double) passed / total * 100) : "N/A";
        }

        public int getTotal() { return total; }
        public int getPassed() { return passed; }
        public String getPassRate() { return passRate; }
    }

    // ==================== Getter / Setter ====================

    public Instant getTimestamp() { return timestamp; }
    public int getTotalCases() { return totalCases; }
    public int getPassedCases() { return passedCases; }
    public int getFailedCases() { return failedCases; }
    public String getPassRate() { return passRate; }
    public long getAvgDurationMs() { return avgDurationMs; }
    public int getTotalTokenEstimate() { return totalTokenEstimate; }
    public String getTotalCostEstimate() { return totalCostEstimate; }
    public Map<String, CategoryStats> getCategoryStats() { return categoryStats; }
    public List<EvalResult> getResults() { return results; }
    public List<Map<String, Object>> getFailures() { return failures; }
}
