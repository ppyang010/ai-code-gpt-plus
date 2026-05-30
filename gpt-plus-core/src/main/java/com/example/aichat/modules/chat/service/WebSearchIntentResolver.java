package com.example.aichat.modules.chat.service;

/**
 * 联网搜索意图解析器，负责把前端三态开关和用户内容折算成后端最终执行决策。
 */
public interface WebSearchIntentResolver {

    /**
     * 解析本次消息是否需要联网搜索，并返回命中的模式或规则信息。
     */
    WebSearchIntentResolution resolve(String webSearchMode, Boolean enableWebSearch, String content);
}
