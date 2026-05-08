package com.example.aichat.modules.chat.dto.stream;

import lombok.Data;

@Data
public class ChatStreamErrorEvent {

    private Long messageId;
    private String errorCode;
    private String errorMessage;
}
