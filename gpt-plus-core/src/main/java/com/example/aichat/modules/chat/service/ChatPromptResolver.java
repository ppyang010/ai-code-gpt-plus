package com.example.aichat.modules.chat.service;

/**
 * 统一系统提示词拼装入口。
 *
 * <p>当前快速 / 思考模式只表示模型原生思考开关，不再承担默认提示词模板切换职责；
 * 因此这里只负责合并会话级和请求级附加提示词。</p>
 */
public interface ChatPromptResolver {

    /**
     * 合并会话级和请求级附加提示词。
     *
     * @param sessionPrompt 会话级附加提示词
     * @param requestPrompt 本次请求级附加提示词
     * @return 最终传给模型的系统提示词；若两者都为空则返回空字符串
     */
    String resolveSystemPrompt(String sessionPrompt, String requestPrompt);
}
