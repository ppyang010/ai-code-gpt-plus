package com.example.aichat.modules.chat.vo;

import com.example.aichat.modules.file.vo.FileAssetItemVO;
import java.time.LocalDateTime;
import java.util.List;
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
    private List<FileAssetItemVO> attachments;
    /** 消息列表回显：该 assistant 消息是否启用联网搜索。 */
    private Boolean webSearchEnabled;
    /** 消息列表回显：该 assistant 消息是否已真实执行外部搜索。 */
    private Boolean webSearchExecuted;
    /** 消息列表回显：联网搜索执行状态。 */
    private String webSearchStatus;
    private LocalDateTime createdAt;
}
