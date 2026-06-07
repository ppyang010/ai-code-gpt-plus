package com.example.aichat.modules.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 后台模型配置状态更新请求。
 */
@Data
public class AdminModelConfigStatusUpdateRequest {

    /** 目标模型 ID。 */
    @NotNull(message = "modelId must not be null")
    private Long modelId;

    /** 目标状态。 */
    @NotNull(message = "status must not be null")
    private Integer status;
}
