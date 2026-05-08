package com.example.aichat.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.model.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_message")
public class ChatMessageDO extends BaseDO {

    @TableField("session_id")
    private Long sessionId;

    @TableField("user_id")
    private Long userId;

    @TableField("role")
    private String role;

    @TableField("seq_no")
    private Integer seqNo;

    @TableField("content")
    private String content;

    @TableField("content_format")
    private String contentFormat;

    @TableField("model_id")
    private Long modelId;

    @TableField("finish_reason")
    private String finishReason;

    @TableField("status")
    private Integer status;

    @TableField("prompt_tokens")
    private Integer promptTokens;

    @TableField("completion_tokens")
    private Integer completionTokens;

    @TableField("total_tokens")
    private Integer totalTokens;

    @TableField("metadata")
    private String metadata;
}
