package com.example.aichat.infrastructure.search;

import com.example.aichat.infrastructure.ai.ChatModelClientException;
import com.example.aichat.modules.chat.service.WebSearchResultItem;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Tavily 搜索客户端，负责把项目内搜索请求转换成 Tavily HTTP API 调用并标准化响应。
 */
@Component
public class TavilyWebSearchClient implements WebSearchClient {

    /** 当前客户端支持的 provider 配置值。 */
    private static final String PROVIDER = "tavily";

    /** 联网搜索配置，包含 baseUrl、apiKey、超时和搜索深度。 */
    private final WebSearchProperties properties;
    /** 复用项目统一 JSON 工具，避免不同客户端各自引入解析方式。 */
    private final ObjectMapper objectMapper;
    /** JDK HTTP 客户端，第一版只需要同步短请求即可满足搜索前置流程。 */
    private final HttpClient httpClient;

    /**
     * 初始化 Tavily 客户端，并按配置建立连接超时。
     */
    public TavilyWebSearchClient(WebSearchProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(normalizeTimeoutSeconds()))
                .build();
    }

    /**
     * 只在 provider=tavily 时接管搜索请求，便于后续并存多个搜索供应商客户端。
     */
    @Override
    public boolean supports(String provider) {
        return PROVIDER.equalsIgnoreCase(provider);
    }

    /**
     * 发起 Tavily 搜索请求，并把 answer/results 映射为项目内统一结构。
     */
    @Override
    @SuppressWarnings("unchecked")
    public WebSearchClientResponse search(String query, int maxResults) {
        // Tavily 支持直接返回 answer；第一版打开它，减少后端二次摘要成本。
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query);
        payload.put("search_depth", normalizeSearchDepth());
        payload.put("max_results", Math.max(1, maxResults));
        payload.put("include_answer", Boolean.TRUE);
        payload.put("include_raw_content", Boolean.FALSE);
        payload.put("include_images", Boolean.FALSE);

        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            // Tavily 使用 Bearer Token 鉴权；超时按配置控制，避免阻塞聊天发送链路。
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildSearchUrl()))
                    .timeout(Duration.ofSeconds(normalizeTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                // 统一抛成模型客户端异常形态，方便上层用同一套错误码/HTTP 状态处理供应商失败。
                throw new ChatModelClientException(
                        "WEB_SEARCH_HTTP_ERROR",
                        response.statusCode(),
                        "WEB_SEARCH_HTTP_ERROR",
                        response.body()
                );
            }

            Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {});
            WebSearchClientResponse clientResponse = new WebSearchClientResponse();
            clientResponse.setAnswer(asText(body.get("answer")));
            clientResponse.setResults(toResultItems((List<Map<String, Object>>) body.get("results"), maxResults));
            return clientResponse;
        } catch (ChatModelClientException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ChatModelClientException("WEB_SEARCH_INTERRUPTED", 500, "WEB_SEARCH_INTERRUPTED", exception.getMessage());
        } catch (Exception exception) {
            throw new ChatModelClientException("WEB_SEARCH_REQUEST_FAILED", 502, "WEB_SEARCH_REQUEST_FAILED", exception.getMessage());
        }
    }

    /**
     * 将 Tavily results 数组裁剪并标准化为聊天领域的搜索结果项。
     */
    private List<WebSearchResultItem> toResultItems(List<Map<String, Object>> results, int maxResults) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        List<WebSearchResultItem> items = new ArrayList<>();
        int limit = Math.max(1, maxResults);
        for (Map<String, Object> result : results) {
            if (items.size() >= limit) {
                break;
            }
            if (result == null) {
                continue;
            }

            WebSearchResultItem item = new WebSearchResultItem();
            item.setTitle(asText(result.get("title")));
            item.setUrl(asText(result.get("url")));
            item.setSnippet(firstText(result.get("content"), result.get("snippet")));
            item.setSource(resolveSource(item.getUrl()));
            item.setScore(asDouble(result.get("score")));
            // 标题、URL、摘要全为空的结果对模型无帮助，直接丢弃。
            if (StringUtils.hasText(item.getTitle()) || StringUtils.hasText(item.getUrl()) || StringUtils.hasText(item.getSnippet())) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * 拼接搜索端点地址，允许 application.yml 覆盖 baseUrl。
     */
    private String buildSearchUrl() {
        String baseUrl = properties.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return "https://api.tavily.com/search";
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalized + "/search";
    }

    /**
     * 兜底非法超时配置，避免 0 或负数直接传给 HTTP 客户端。
     */
    private int normalizeTimeoutSeconds() {
        return properties.getTimeoutSeconds() <= 0 ? 10 : properties.getTimeoutSeconds();
    }

    /**
     * 将外部配置限制在 Tavily 当前支持的搜索深度范围内。
     */
    private String normalizeSearchDepth() {
        String depth = properties.getSearchDepth();
        if (!StringUtils.hasText(depth)) {
            return "basic";
        }
        String normalized = depth.trim().toLowerCase(Locale.ROOT);
        return "advanced".equals(normalized) ? "advanced" : "basic";
    }

    /**
     * 在多个候选字段里取第一个非空文本，用于兼容不同供应商或响应版本字段。
     */
    private String firstText(Object... values) {
        for (Object value : values) {
            String text = asText(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    /**
     * 将供应商返回值安全转成去空白文本。
     */
    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }

    /**
     * 将供应商相关性分数转换成 Double，解析失败时保留为空。
     */
    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 从 URL 中提取域名，前端和 prompt 都用它作为更短的来源展示。
     */
    private String resolveSource(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            return URI.create(url).getHost();
        } catch (Exception exception) {
            return null;
        }
    }
}
