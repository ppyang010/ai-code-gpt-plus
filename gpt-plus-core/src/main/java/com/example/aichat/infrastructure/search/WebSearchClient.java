package com.example.aichat.infrastructure.search;

/**
 * 联网搜索供应商客户端抽象，聊天业务只依赖统一的搜索输入输出，不感知具体供应商协议。
 */
public interface WebSearchClient {

    /**
     * 判断当前客户端是否支持配置中的供应商标识，用于后续扩展 Bing、SerpAPI 等实现。
     */
    boolean supports(String provider);

    /**
     * 执行真实联网搜索，并把供应商响应转换为项目内统一响应对象。
     */
    WebSearchClientResponse search(String query, int maxResults);
}
