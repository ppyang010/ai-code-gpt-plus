package com.example.aichat.modules.model.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 后台模型配置响应对象。
 */
@Data
public class AdminModelConfigVO {

    /** 模型 ID。 */
    private Long modelId;

    /** 供应商 ID。 */
    private Long providerId;

    /** 供应商编码。 */
    private String providerCode;

    /** 供应商名称。 */
    private String providerName;

    /** 模型编码。 */
    private String modelCode;

    /** 模型名称。 */
    private String modelName;

    /** 模型类型。 */
    private String modelType;

    /** 是否支持流式。 */
    private Integer supportStream;

    /** 是否支持思考模式。 */
    private Integer supportThinking;

    /** 是否支持 JSON 输出。 */
    private Integer supportJsonOutput;

    /** 是否支持图片。 */
    private Integer supportVision;

    /** 是否支持文件。 */
    private Integer supportFile;

    /** 上下文窗口大小。 */
    private Integer contextWindow;

    /** 最大输出 token 数。 */
    private Integer maxOutputTokens;

    /** 默认 temperature。 */
    private BigDecimal temperatureDefault;

    /** 默认 topP。 */
    private BigDecimal topPDefault;

    /** 扩展配置 JSON 字符串。 */
    private String extraConfig;

    /** 模型状态。 */
    private Integer status;

    /** 排序值。 */
    private Integer sortNo;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
