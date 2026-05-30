package com.example.aichat.modules.chat.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 会话列表项响应对象，承载列表展示所需的标题、模型、最近消息和首条用户问题摘要。
 */
@Data
public class ChatSessionListItemVO {

    private Long sessionId;
    private String title;
    private String modeCode;
    private Long defaultModelId;
    private String defaultModelCode;
    private String defaultModelName;
    private String lastMessagePreview;
    /** 首条用户消息摘要，用于默认标题会话的列表展示兜底，避免误用 assistant 回答摘要。 */
    private String firstUserMessagePreview;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
}
