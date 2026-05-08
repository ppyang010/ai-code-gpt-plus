package com.example.aichat.modules.chat.dto.stream;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ChatStreamEndEvent {

    private Long messageId;
    private String finishReason;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private LocalDateTime createdAt;
}
