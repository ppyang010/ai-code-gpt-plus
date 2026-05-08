package com.example.aichat.modules.chat.dto;

import lombok.Data;

@Data
public class ChatMessageSendRequest {

    private Long sessionId;
    private String content;
    private Long modelId;
    private String modeCode;
    private String systemPrompt;
    private Boolean enableDeepThinking;
    private Boolean enableWebSearch;
    private Long regenerateMessageId;
}
