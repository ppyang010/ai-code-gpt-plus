package com.example.aichat.modules.chat.service.impl;

import com.example.aichat.common.enums.ChatModeEnum;
import com.example.aichat.modules.chat.service.ChatPromptResolver;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DefaultChatPromptResolver implements ChatPromptResolver {

    private final Map<String, String> modePromptTemplates = new LinkedHashMap<>();

    public DefaultChatPromptResolver() {
        modePromptTemplates.put(
                ChatModeEnum.QUICK.getCode(),
                "You are a concise and efficient AI assistant.\n"
                        + "Prioritize direct answers and practical next steps.\n"
                        + "Keep the response clear and compact."
        );
        modePromptTemplates.put(
                ChatModeEnum.EXPERT.getCode(),
                "You are a professional consultant-style AI assistant.\n"
                        + "First understand the problem, then provide a structured answer.\n"
                        + "Include reasoning, tradeoffs, risks, and actionable suggestions when relevant."
        );
    }

    @Override
    public String resolveSystemPrompt(String modeCode, String sessionPrompt, String requestPrompt) {
        StringBuilder builder = new StringBuilder();
        append(builder, modePromptTemplates.get(ChatModeEnum.fromCodeOrDefault(modeCode).getCode()));
        append(builder, sessionPrompt);
        append(builder, requestPrompt);
        return builder.toString();
    }

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
