package com.example.aichat.modules.chat.dto.stream;

import lombok.Data;

/**
 * assistant 原生思考过程的流式增量事件。
 */
@Data
public class ChatStreamReasoningDeltaEvent {

    /** 当前思考过程所属的 assistant 消息 ID。 */
    private Long messageId;
    /** 本次新增的思考过程文本片段。 */
    private String reasoningDelta;
}
