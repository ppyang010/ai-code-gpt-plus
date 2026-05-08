package com.example.aichat.modules.billing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.model.BaseDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_token_usage")
public class UserTokenUsageDO extends BaseDO {

    @TableField("user_id")
    private Long userId;

    @TableField("session_id")
    private Long sessionId;

    @TableField("message_id")
    private Long messageId;

    @TableField("api_call_log_id")
    private Long apiCallLogId;

    @TableField("provider_id")
    private Long providerId;

    @TableField("model_id")
    private Long modelId;

    @TableField("prompt_tokens")
    private Integer promptTokens;

    @TableField("completion_tokens")
    private Integer completionTokens;

    @TableField("total_tokens")
    private Integer totalTokens;

    @TableField("estimated_cost")
    private BigDecimal estimatedCost;

    @TableField("stat_date")
    private LocalDate statDate;
}
