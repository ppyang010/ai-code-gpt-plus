package com.example.aichat.modules.chat.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ChatSessionListItemVO {

    private Long sessionId;
    private String title;
    private String modeCode;
    private Long defaultModelId;
    private String defaultModelCode;
    private String defaultModelName;
    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
}
