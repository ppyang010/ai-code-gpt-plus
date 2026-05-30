package com.example.aichat.modules.chat.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次聊天消息的联网搜索上下文，贯穿搜索执行、prompt 注入、metadata 持久化和前端回显。
 */
public class WebSearchContext {

    /** 后端判定不需要联网搜索。 */
    public static final String STATUS_DISABLED = "disabled";
    /** 曾计划执行搜索但被配置或客户端条件跳过。 */
    public static final String STATUS_SKIPPED = "skipped";
    /** 已完成真实搜索并生成摘要。 */
    public static final String STATUS_SUCCESS = "success";
    /** 搜索供应商调用失败。 */
    public static final String STATUS_FAILED = "failed";

    /** 本次消息最终是否需要联网搜索。 */
    private boolean enabled;
    /** 本次消息是否已经真实执行过外部搜索。 */
    private boolean executed;
    /** 搜索状态，供前端提示和后续排障使用。 */
    private String status;
    /** 用户选择的联网搜索模式：disabled / enabled / auto。 */
    private String mode;
    /** 自动判断或手动模式命中的规则 ID。 */
    private String ruleId;
    /** 自动判断或手动模式命中的规则展示名。 */
    private String ruleLabel;
    /** 实际提交给搜索供应商的查询文本。 */
    private String query;
    /** 可直接追加到模型系统提示词的搜索摘要。 */
    private String summary;
    /** 标准化搜索结果列表，持久化后用于继续生成复用。 */
    private List<WebSearchResultItem> results = new ArrayList<>();
    /** 搜索失败或跳过时的内部错误码。 */
    private String errorCode;
    /** 搜索失败或跳过时的可读错误说明。 */
    private String errorMessage;

    /**
     * 构造“未开启搜索”的上下文，保留模式和规则信息用于 metadata 回显。
     */
    public static WebSearchContext disabled(WebSearchIntentResolution resolution) {
        WebSearchContext context = fromResolution(resolution);
        context.setEnabled(false);
        context.setExecuted(false);
        context.setStatus(STATUS_DISABLED);
        return context;
    }

    /**
     * 构造“已开启但未执行”的上下文，第一版主要用于可恢复的配置或客户端缺失场景。
     */
    public static WebSearchContext skipped(WebSearchIntentResolution resolution, String query, String errorCode, String errorMessage) {
        WebSearchContext context = fromResolution(resolution);
        context.setEnabled(true);
        context.setExecuted(false);
        context.setStatus(STATUS_SKIPPED);
        context.setQuery(query);
        context.setErrorCode(errorCode);
        context.setErrorMessage(errorMessage);
        return context;
    }

    /**
     * 构造“搜索成功”的上下文，summary 会被后续注入模型 prompt。
     */
    public static WebSearchContext success(
            WebSearchIntentResolution resolution,
            String query,
            String summary,
            List<WebSearchResultItem> results
    ) {
        WebSearchContext context = fromResolution(resolution);
        context.setEnabled(true);
        context.setExecuted(true);
        context.setStatus(STATUS_SUCCESS);
        context.setQuery(query);
        context.setSummary(summary);
        context.setResults(results == null ? List.of() : results);
        return context;
    }

    /**
     * 构造“搜索失败”的上下文，保留错误信息用于排障或前端提示。
     */
    public static WebSearchContext failed(WebSearchIntentResolution resolution, String query, String errorCode, String errorMessage) {
        WebSearchContext context = fromResolution(resolution);
        context.setEnabled(true);
        context.setExecuted(false);
        context.setStatus(STATUS_FAILED);
        context.setQuery(query);
        context.setErrorCode(errorCode);
        context.setErrorMessage(errorMessage);
        return context;
    }

    /**
     * 从意图判定结果初始化基础上下文，确保后续所有状态都能追溯到模式和规则。
     */
    public static WebSearchContext fromResolution(WebSearchIntentResolution resolution) {
        WebSearchContext context = new WebSearchContext();
        if (resolution != null) {
            context.setEnabled(resolution.isEnabled());
            context.setMode(resolution.getMode());
            context.setRuleId(resolution.getMatchedRuleId());
            context.setRuleLabel(resolution.getMatchedRuleLabel());
        }
        return context;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleLabel() {
        return ruleLabel;
    }

    public void setRuleLabel(String ruleLabel) {
        this.ruleLabel = ruleLabel;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<WebSearchResultItem> getResults() {
        return results;
    }

    public void setResults(List<WebSearchResultItem> results) {
        this.results = results;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
