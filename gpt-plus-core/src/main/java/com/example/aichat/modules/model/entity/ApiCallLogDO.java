package com.example.aichat.modules.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("api_call_log")
public class ApiCallLogDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("session_id")
    private Long sessionId;

    @TableField("message_id")
    private Long messageId;

    @TableField("provider_id")
    private Long providerId;

    @TableField("model_id")
    private Long modelId;

    @TableField("request_id")
    private String requestId;

    @TableField("is_stream")
    private Integer isStream;

    @TableField("success_flag")
    private Integer successFlag;

    @TableField("http_status")
    private Integer httpStatus;

    @TableField("latency_ms")
    private Integer latencyMs;

    @TableField("prompt_tokens")
    private Integer promptTokens;

    @TableField("completion_tokens")
    private Integer completionTokens;

    @TableField("total_tokens")
    private Integer totalTokens;

    @TableField("estimated_cost")
    private BigDecimal estimatedCost;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_message")
    private String errorMessage;

    @TableField("request_payload")
    private String requestPayload;

    @TableField("response_payload")
    private String responsePayload;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
