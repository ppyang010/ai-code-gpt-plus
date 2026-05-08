package com.example.aichat.modules.chat.dto;

import lombok.Data;

@Data
public class ChatMessageRegenerateRequest {

    private Long sessionId;
    private Long regenerateMessageId;
    private Long modelId;
    private String modeCode;
}
