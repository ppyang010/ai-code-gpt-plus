package com.example.aichat.modules.chat.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatSessionCreateRequest {

    @Size(max = 255, message = "length must be at most 255")
    private String title;

    @Size(max = 32, message = "length must be at most 32")
    private String modeCode;

    private Long defaultModelId;

    @Size(max = 4000, message = "length must be at most 4000")
    private String systemPrompt;
}
