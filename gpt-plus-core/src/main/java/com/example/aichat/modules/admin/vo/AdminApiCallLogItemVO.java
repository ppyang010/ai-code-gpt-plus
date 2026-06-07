package com.example.aichat.modules.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 后台模型调用日志响应对象。
 */
@Data
public class AdminApiCallLogItemVO {

    /** 调用日志 ID。 */
    private Long logId;

    /** 用户 ID。 */
    private Long userId;

    /** 会话 ID。 */
    private Long sessionId;

    /** 消息 ID。 */
    private Long messageId;

    /** 请求流水号。 */
    private String requestId;

    /** 供应商 ID。 */
    private Long providerId;

    /** 供应商名称。 */
    private String providerName;

    /** 模型 ID。 */
    private Long modelId;

    /** 模型编码。 */
    private String modelCode;

    /** 模型名称。 */
    private String modelName;

    /** 是否调用成功。 */
    private Integer successFlag;

    /** HTTP 状态码。 */
    private Integer httpStatus;

    /** 耗时毫秒数。 */
    private Integer latencyMs;

    /** 输入 token 数。 */
    private Integer promptTokens;

    /** 输出 token 数。 */
    private Integer completionTokens;

    /** 总 token 数。 */
    private Integer totalTokens;

    /** 预估成本。 */
    private BigDecimal estimatedCost;

    /** 错误码。 */
    private String errorCode;

    /** 错误信息。 */
    private String errorMessage;

    /** 请求摘要 JSON。 */
    private String requestPayload;

    /** 响应摘要 JSON。 */
    private String responsePayload;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
