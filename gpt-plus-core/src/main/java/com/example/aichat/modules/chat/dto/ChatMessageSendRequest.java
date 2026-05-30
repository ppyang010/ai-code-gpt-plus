package com.example.aichat.modules.chat.dto;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatMessageSendRequest {

    @NotNull(message = "must not be null")
    private Long sessionId;

    @NotBlank(message = "must not be blank")
    @Size(max = 8000, message = "length must be at most 8000")
    private String content;

    private Long modelId;

    @Size(max = 32, message = "length must be at most 32")
    private String modeCode;

    @Size(max = 4000, message = "length must be at most 4000")
    private String systemPrompt;

    private List<Long> attachmentIds;

    private Boolean enableDeepThinking;
    private Boolean enableWebSearch;

    @Size(max = 16, message = "length must be at most 16")
    private String webSearchMode;

    private Long regenerateMessageId;
}
