package com.example.aichat.modules.chat.vo;

import com.example.aichat.modules.file.vo.FileAssetItemVO;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 消息列表单条消息视图对象。
 */
@Data
public class ChatMessageItemVO {

    private Long messageId;
    private String role;
    private String content;
    /** assistant 原生思考过程全文，供刷新后回显使用。 */
    private String reasoningContent;
    /** assistant 消息是否开启过模型原生思考模式，供前端决定是否显示“思考中”占位。 */
    private Boolean deepThinkingEnabled;
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
    private List<FileAssetItemVO> attachments;
    /** 消息列表回显：该 assistant 消息是否启用联网搜索。 */
    private Boolean webSearchEnabled;
    /** 消息列表回显：该 assistant 消息是否已真实执行外部搜索。 */
    private Boolean webSearchExecuted;
    /** 消息列表回显：联网搜索执行状态。 */
    private String webSearchStatus;
    private LocalDateTime createdAt;
}
