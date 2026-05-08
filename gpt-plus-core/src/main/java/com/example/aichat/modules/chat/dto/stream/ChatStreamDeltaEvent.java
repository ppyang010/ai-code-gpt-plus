package com.example.aichat.modules.chat.dto.stream;

import lombok.Data;

@Data
public class ChatStreamDeltaEvent {

    private Long messageId;
    private String delta;
}
