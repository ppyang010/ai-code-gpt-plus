package com.example.aichat.modules.chat.service;

/**
 * 聊天领域内的标准搜索结果项，用来同时支撑 prompt 注入和消息 metadata 持久化。
 */
public class WebSearchResultItem {

    /** 搜索结果标题。 */
    private String title;
    /** 搜索结果原始 URL。 */
    private String url;
    /** 搜索结果摘要或内容片段。 */
    private String snippet;
    /** 从 URL 提取出的来源域名。 */
    private String source;
    /** 供应商返回的相关性分数，可能为空。 */
    private Double score;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }
}
