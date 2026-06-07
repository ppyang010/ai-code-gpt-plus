package com.example.aichat.modules.chat.service.impl;

import com.example.aichat.modules.chat.service.ChatPromptResolver;
import com.example.aichat.modules.prompt.service.PromptTemplateService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 默认系统提示词拼装器。
 *
 * <p>当前 quick / expert 已经收敛为模型原生思考开关，因此这里不再根据模式注入默认模板，
 * 只负责合并会话级和请求级附加提示词。</p>
 */
@Component
public class DefaultChatPromptResolver implements ChatPromptResolver {

    /** 提示词模板服务，用于拼装当前启用的全局附加提示词。 */
    private final PromptTemplateService promptTemplateService;

    public DefaultChatPromptResolver(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @Override
    public String resolveSystemPrompt(String sessionPrompt, String requestPrompt) {
        StringBuilder builder = new StringBuilder();
        this.appendEnabledGlobalTemplates(builder);
        // 当前只合并用户显式提供的附加提示词，避免模式切换再隐式改变系统 prompt。
        this.append(builder, sessionPrompt);
        this.append(builder, requestPrompt);
        return builder.toString();
    }

    /**
     * 优先追加当前启用的全局附加提示词模板；模板链路异常时静默回退到原有会话级 / 请求级合并逻辑。
     */
    private void appendEnabledGlobalTemplates(StringBuilder builder) {
        try {
            for (String promptTemplateContent : this.promptTemplateService.listEnabledGlobalPromptContents()) {
                this.append(builder, promptTemplateContent);
            }
        } catch (Exception exception) {
            // 提示词模板链路异常时，不影响主聊天链路继续使用会话级 / 请求级附加提示词。
        }
    }

    /**
     * 以双换行拼接每一段非空提示词，保留调用方输入的自然段边界。
     */
    private void append(StringBuilder builder, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(content.trim());
    }
}
