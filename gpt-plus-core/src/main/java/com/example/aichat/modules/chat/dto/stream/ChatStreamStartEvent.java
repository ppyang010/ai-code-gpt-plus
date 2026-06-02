package com.example.aichat.modules.chat.dto.stream;

import lombok.Data;

@Data
public class ChatStreamStartEvent {

    private Long sessionId;
    private Long messageId;
    private String role;
    private Long modelId;
    private String modelCode;
    private String modelName;
    /** 本次 assistant 回复是否开启了模型原生思考模式。 */
    private Boolean deepThinkingEnabled;
    /** 本次 assistant 回答是否被判定为联网搜索回答。 */
    private Boolean webSearchEnabled;
    /** 本次 assistant 回答是否已经真实执行过搜索。 */
    private Boolean webSearchExecuted;
    /** 本次联网搜索状态，用于前端流式开始时立即展示提示。 */
    private String webSearchStatus;
}
