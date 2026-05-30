package com.example.aichat.modules.chat.service;

/**
 * 聊天领域的联网搜索服务，负责按意图判定结果执行搜索并生成可注入模型的上下文。
 */
public interface WebSearchService {

    /**
     * 根据用户问题和后端意图判定结果，返回本次消息对应的搜索上下文。
     */
    WebSearchContext search(String query, WebSearchIntentResolution resolution);
}
