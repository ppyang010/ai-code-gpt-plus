package com.example.aichat.modules.chat.dto.stream;

import lombok.Data;

@Data
public class ChatStreamStartEvent {

    private Long sessionId;
    private Long messageId;
    private String role;
    private Long modelId;
    private String modelCode;
    private String modelName;
}
