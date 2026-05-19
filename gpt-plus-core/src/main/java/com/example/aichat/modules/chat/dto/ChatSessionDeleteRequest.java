package com.example.aichat.modules.chat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatSessionDeleteRequest {

    @NotNull(message = "must not be null")
    private Long sessionId;
}
