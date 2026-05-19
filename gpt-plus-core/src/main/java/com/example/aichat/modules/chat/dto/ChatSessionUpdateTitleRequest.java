package com.example.aichat.modules.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatSessionUpdateTitleRequest {

    @NotNull(message = "must not be null")
    private Long sessionId;

    @NotBlank(message = "must not be blank")
    @Size(max = 255, message = "length must be at most 255")
    private String title;
}
