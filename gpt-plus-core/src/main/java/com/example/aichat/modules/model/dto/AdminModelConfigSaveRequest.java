package com.example.aichat.modules.model.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台模型配置新增或编辑请求。
 */
@Data
public class AdminModelConfigSaveRequest {

    /** 模型 ID；创建时可为空，更新时由后续逻辑校验必填。 */
    private Long modelId;

    /** 供应商 ID。 */
    private Long providerId;

    /** 模型编码。 */
    @Size(max = 128, message = "modelCode length must be at most 128")
    private String modelCode;

    /** 模型名称。 */
    @Size(max = 128, message = "modelName length must be at most 128")
    private String modelName;

    /** 模型类型。 */
    @Size(max = 32, message = "modelType length must be at most 32")
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
    @Size(max = 4000, message = "extraConfig length must be at most 4000")
    private String extraConfig;

    /** 模型状态。 */
    private Integer status;

    /** 排序值。 */
    private Integer sortNo;
}
