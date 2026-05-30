package com.example.aichat.modules.chat.service.impl;

import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.infrastructure.search.WebSearchClient;
import com.example.aichat.infrastructure.search.WebSearchClientResponse;
import com.example.aichat.infrastructure.search.WebSearchProperties;
import com.example.aichat.modules.chat.service.WebSearchContext;
import com.example.aichat.modules.chat.service.WebSearchIntentResolution;
import com.example.aichat.modules.chat.service.WebSearchResultItem;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 联网搜索领域服务单测，覆盖不搜索、配置缺失和搜索成功三条核心路径。
 */
class DefaultWebSearchServiceTest {

    /**
     * 意图判定不需要联网时，服务应返回 disabled 上下文且不触发客户端调用。
     */
    @Test
    void shouldReturnDisabledContextWhenIntentDoesNotRequireSearch() {
        DefaultWebSearchService service = new DefaultWebSearchService(new WebSearchProperties(), List.of(new FakeWebSearchClient()));

        WebSearchContext context = service.search("解释一下 Java 泛型", resolution(false));

        assertFalse(context.isEnabled());
        assertFalse(context.isExecuted());
        assertEquals(WebSearchContext.STATUS_DISABLED, context.getStatus());
    }

    /**
     * 后端已经判定需要联网时，搜索配置未开启必须显式失败。
     */
    @Test
    void shouldFailFastWhenSearchIsEnabledButConfigDisabled() {
        DefaultWebSearchService service = new DefaultWebSearchService(new WebSearchProperties(), List.of(new FakeWebSearchClient()));

        BizException exception = assertThrows(BizException.class, () -> service.search("今天汇率", resolution(true)));

        assertEquals(ErrorCode.WEB_SEARCH_NOT_CONFIGURED.getCode(), exception.getCode());
    }

    /**
     * 搜索配置完整时，应调用客户端并生成可注入模型 prompt 的摘要。
     */
    @Test
    void shouldExecuteClientAndBuildSummaryWhenConfigured() {
        WebSearchProperties properties = new WebSearchProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setProvider("fake");

        DefaultWebSearchService service = new DefaultWebSearchService(properties, List.of(new FakeWebSearchClient()));

        WebSearchContext context = service.search("今天汇率", resolution(true));

        assertTrue(context.isEnabled());
        assertTrue(context.isExecuted());
        assertEquals(WebSearchContext.STATUS_SUCCESS, context.getStatus());
        assertEquals(1, context.getResults().size());
        assertTrue(context.getSummary().contains("搜索摘要"));
        assertTrue(context.getSummary().contains("https://example.com/rate"));
    }

    /**
     * 构造固定意图解析结果，降低测试对规则解析器的耦合。
     */
    private WebSearchIntentResolution resolution(boolean enabled) {
        return new WebSearchIntentResolution("auto", enabled, "test_rule", "测试规则", enabled ? "enable" : "disable");
    }

    /**
     * 测试专用搜索客户端，避免单测发起真实外部网络请求。
     */
    private static class FakeWebSearchClient implements WebSearchClient {

        /**
         * 只响应测试里显式配置的 fake provider。
         */
        @Override
        public boolean supports(String provider) {
            return "fake".equals(provider);
        }

        /**
         * 返回一条固定搜索结果，用于验证摘要拼接逻辑。
         */
        @Override
        public WebSearchClientResponse search(String query, int maxResults) {
            WebSearchResultItem result = new WebSearchResultItem();
            result.setTitle("汇率参考");
            result.setUrl("https://example.com/rate");
            result.setSnippet("美元人民币汇率示例。");
            result.setSource("example.com");

            WebSearchClientResponse response = new WebSearchClientResponse();
            response.setAnswer("这是搜索答案摘要。");
            response.setResults(List.of(result));
            return response;
        }
    }
}
