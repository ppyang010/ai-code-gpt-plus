package com.example.aichat.modules.chat.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ChatSessionCreateVO {

    private Long sessionId;
    private String title;
    private String modeCode;
    private Long defaultModelId;
    private LocalDateTime createdAt;
}
