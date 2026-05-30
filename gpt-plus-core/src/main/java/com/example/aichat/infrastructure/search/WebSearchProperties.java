package com.example.aichat.infrastructure.search;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 联网搜索配置项，集中承载供应商选择、鉴权、超时和结果数量控制。
 */
@Component
@ConfigurationProperties(prefix = "app.web-search")
public class WebSearchProperties {

    /** 是否允许后端真正发起外部搜索请求。 */
    private boolean enabled = false;
    /** 搜索供应商标识，默认使用 Tavily。 */
    private String provider = "tavily";
    /** 搜索供应商基础地址，便于本地代理或后续私有网关替换。 */
    private String baseUrl = "https://api.tavily.com";
    /** 搜索供应商 API Key，未配置时后端会显式报错。 */
    private String apiKey;
    /** 返回给模型上下文的最大搜索结果数。 */
    private int maxResults = 5;
    /** 单次搜索请求超时时间，单位秒。 */
    private int timeoutSeconds = 10;
    /** Tavily 搜索深度，第一版只允许 basic / advanced。 */
    private String searchDepth = "basic";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getSearchDepth() {
        return searchDepth;
    }

    public void setSearchDepth(String searchDepth) {
        this.searchDepth = searchDepth;
    }
}
