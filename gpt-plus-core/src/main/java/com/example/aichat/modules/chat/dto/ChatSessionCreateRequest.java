package com.example.aichat.modules.chat.dto;

import lombok.Data;

@Data
public class ChatSessionCreateRequest {

    private String title;
    private String modeCode;
    private Long defaultModelId;
    private String systemPrompt;
}
