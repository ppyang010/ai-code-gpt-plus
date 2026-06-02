package com.example.aichat.modules.chat.dto.stream;

import lombok.Data;

/**
 * assistant 最终回答正文的流式增量事件。
 */
@Data
public class ChatStreamDeltaEvent {

    private Long messageId;
    /** 本次新增的回答正文片段。 */
    private String delta;
}
