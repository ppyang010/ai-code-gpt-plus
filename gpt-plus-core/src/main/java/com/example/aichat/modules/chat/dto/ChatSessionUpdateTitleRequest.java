package com.example.aichat.modules.chat.dto;

import lombok.Data;

@Data
public class ChatSessionUpdateTitleRequest {

    private Long sessionId;
    private String title;
}
