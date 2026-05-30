package com.example.aichat.modules.chat.service.impl;

import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.infrastructure.ai.ChatModelClientException;
import com.example.aichat.infrastructure.search.WebSearchClient;
import com.example.aichat.infrastructure.search.WebSearchClientResponse;
import com.example.aichat.infrastructure.search.WebSearchProperties;
import com.example.aichat.modules.chat.service.WebSearchContext;
import com.example.aichat.modules.chat.service.WebSearchIntentResolution;
import com.example.aichat.modules.chat.service.WebSearchResultItem;
import com.example.aichat.modules.chat.service.WebSearchService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 默认联网搜索领域服务，负责编排配置校验、供应商选择、结果裁剪和 prompt 摘要构造。
 */
@Service
public class DefaultWebSearchService implements WebSearchService {

    /** 搜索失败只记录供应商细节，返回给前端时统一成业务错误。 */
    private static final Logger log = LoggerFactory.getLogger(DefaultWebSearchService.class);

    /** 联网搜索运行配置。 */
    private final WebSearchProperties properties;
    /** Spring 注入的所有搜索客户端，按 provider 动态选择。 */
    private final List<WebSearchClient> clients;

    /**
     * 初始化默认搜索服务，保留客户端列表以支持多供应商并存。
     */
    public DefaultWebSearchService(WebSearchProperties properties, List<WebSearchClient> clients) {
        this.properties = properties;
        this.clients = clients;
    }

    /**
     * 根据意图判定结果决定是否执行真实搜索，并返回可注入模型上下文的搜索结果。
     */
    @Override
    public WebSearchContext search(String query, WebSearchIntentResolution resolution) {
        if (resolution == null || !resolution.isEnabled()) {
            return WebSearchContext.disabled(resolution);
        }

        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : "";
        // 只要后端已经判定“需要联网”，配置缺失就显式失败，避免用户误以为回答已经联网。
        if (!properties.isEnabled()) {
            throw new BizException(ErrorCode.WEB_SEARCH_NOT_CONFIGURED, "联网搜索配置未开启");
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new BizException(ErrorCode.WEB_SEARCH_NOT_CONFIGURED, "联网搜索 API Key 未配置");
        }
        WebSearchClient client = resolveClient();
        if (client == null) {
            throw new BizException(ErrorCode.WEB_SEARCH_NOT_CONFIGURED, "未找到可用的联网搜索客户端");
        }

        try {
            // 供应商响应先标准化，再由领域服务统一生成适合模型读取的摘要文本。
            WebSearchClientResponse response = client.search(normalizedQuery, normalizeMaxResults());
            List<WebSearchResultItem> results = response.getResults() == null ? List.of() : response.getResults();
            String summary = buildSummary(response.getAnswer(), results);
            return WebSearchContext.success(resolution, normalizedQuery, summary, results);
        } catch (ChatModelClientException exception) {
            log.warn(
                    "Web search failed provider={} mode={} ruleId={} errorCode={} httpStatus={}",
                    properties.getProvider(),
                    resolution.getMode(),
                    resolution.getMatchedRuleId(),
                    exception.getErrorCode(),
                    exception.getHttpStatus()
            );
            throw new BizException(ErrorCode.WEB_SEARCH_FAILED);
        } catch (Exception exception) {
            log.warn(
                    "Web search failed provider={} mode={} ruleId={} errorType={}",
                    properties.getProvider(),
                    resolution.getMode(),
                    resolution.getMatchedRuleId(),
                    exception.getClass().getSimpleName()
            );
            throw new BizException(ErrorCode.WEB_SEARCH_FAILED);
        }
    }

    /**
     * 根据配置中的 provider 选择具体搜索客户端。
     */
    private WebSearchClient resolveClient() {
        if (clients == null || clients.isEmpty()) {
            return null;
        }
        for (WebSearchClient client : clients) {
            if (client.supports(properties.getProvider())) {
                return client;
            }
        }
        return null;
    }

    /**
     * 限制最大结果数，防止配置过大导致 prompt 膨胀。
     */
    private int normalizeMaxResults() {
        int maxResults = properties.getMaxResults();
        if (maxResults <= 0) {
            return 5;
        }
        return Math.min(maxResults, 10);
    }

    /**
     * 把标准化搜索结果整理成模型容易读取的引用摘要。
     */
    private String buildSummary(String answer, List<WebSearchResultItem> results) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(answer)) {
            builder.append("搜索摘要：").append(answer.trim()).append("\n\n");
        }
        if (results != null && !results.isEmpty()) {
            builder.append("搜索结果：\n");
            for (int index = 0; index < results.size(); index++) {
                WebSearchResultItem result = results.get(index);
                builder.append("[").append(index + 1).append("] ");
                builder.append(StringUtils.hasText(result.getTitle()) ? result.getTitle().trim() : "未命名来源");
                // source 只放域名，完整 URL 单独占一行，降低模型阅读噪声。
                if (StringUtils.hasText(result.getSource())) {
                    builder.append(" - ").append(result.getSource().trim());
                }
                builder.append("\n");
                if (StringUtils.hasText(result.getUrl())) {
                    builder.append("URL: ").append(result.getUrl().trim()).append("\n");
                }
                if (StringUtils.hasText(result.getSnippet())) {
                    builder.append("摘要: ").append(result.getSnippet().trim()).append("\n");
                }
            }
        }
        return builder.toString().trim();
    }
}
