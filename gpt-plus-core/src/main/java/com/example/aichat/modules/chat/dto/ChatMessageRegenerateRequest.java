package com.example.aichat.modules.chat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatMessageRegenerateRequest {

    @NotNull(message = "must not be null")
    private Long sessionId;

    @NotNull(message = "must not be null")
    private Long regenerateMessageId;

    private Long modelId;
    private String modeCode;
    /** 是否开启模型原生思考模式；未显式传值时由 `modeCode` 折算。 */
    private Boolean enableDeepThinking;
}
