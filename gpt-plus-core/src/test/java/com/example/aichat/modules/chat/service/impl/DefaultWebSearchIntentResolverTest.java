package com.example.aichat.modules.chat.service.impl;

import com.example.aichat.common.exception.BizException;
import com.example.aichat.modules.chat.service.WebSearchIntentResolution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 联网搜索意图解析单测，覆盖第一版自动判断和非法模式校验。
 */
class DefaultWebSearchIntentResolverTest {

    /** 被测解析器，规则表固定在实现类内部。 */
    private final DefaultWebSearchIntentResolver resolver = new DefaultWebSearchIntentResolver();

    /**
     * 显式查询意图应开启联网搜索，并返回命中的规则 ID。
     */
    @Test
    void shouldEnableWebSearchForExplicitLookupIntent() {
        WebSearchIntentResolution resolution = resolver.resolve("auto", null, "帮我查一下今天 OpenAI 最新消息");

        assertTrue(resolution.isEnabled());
        assertEquals("auto", resolution.getMode());
        assertEquals("explicit_web_lookup", resolution.getMatchedRuleId());
    }

    /**
     * 写作润色类意图不需要联网搜索，避免无意义外部请求。
     */
    @Test
    void shouldDisableWebSearchForWritingIntent() {
        WebSearchIntentResolution resolution = resolver.resolve("auto", null, "帮我写一封项目周报邮件");

        assertFalse(resolution.isEnabled());
        assertEquals("auto", resolution.getMode());
        assertEquals("writing_and_polish", resolution.getMatchedRuleId());
    }

    /**
     * 非法三态模式需要提前失败，防止进入发送链路后才暴露问题。
     */
    @Test
    void shouldRejectUnsupportedMode() {
        assertThrows(BizException.class, () -> resolver.resolve("always", null, "hello"));
    }
}
