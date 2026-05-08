package com.example.aichat.modules.chat.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ChatMessageItemVO {

    private Long messageId;
    private String role;
    private String content;
    private String contentFormat;
    private Integer seqNo;
    private Long modelId;
    private String modelCode;
    private String modelName;
    private String finishReason;
    private Integer status;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private LocalDateTime createdAt;
}
