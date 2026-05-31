package com.example.aichat.infrastructure.ai.openai;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OpenAI-compatible 供应商配置，按 providerCode 维护各供应商本地调用参数。
 */
@Component
@ConfigurationProperties(prefix = "ai.openai-compatible")
public class OpenAiCompatibleProperties {

    /** 总开关，便于本地一次性关闭所有通用兼容供应商。 */
    private boolean enabled = true;
    /** 供应商配置集合，key 必须和 model_provider.provider_code 保持一致。 */
    private Map<String, Provider> providers = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Provider> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, Provider> providers) {
        this.providers = providers;
    }

    /**
     * 按供应商编码读取配置，隐藏 Map 判空和 key 规整细节。
     */
    public Provider getProvider(String providerCode) {
        if (providerCode == null || providers == null || providers.isEmpty()) {
            return null;
        }
        return providers.get(providerCode.trim());
    }

    /**
     * 单个 OpenAI-compatible 供应商的本地调用配置。
     */
    public static class Provider {

        /** 单供应商开关，避免占位配置在未填 key 时被误用。 */
        private boolean enabled = false;
        /** 供应商基础地址，客户端会自动拼接 /chat/completions。 */
        private String baseUrl;
        /** 真实 API Key，只能来自本地忽略文件或环境变量。 */
        private String apiKey;
        /** 可选默认请求头，用于 OpenRouter 等需要额外来源标识的供应商。 */
        private Map<String, String> defaultHeaders = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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

        public Map<String, String> getDefaultHeaders() {
            return defaultHeaders;
        }

        public void setDefaultHeaders(Map<String, String> defaultHeaders) {
            this.defaultHeaders = defaultHeaders;
        }
    }
}
