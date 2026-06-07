package com.example.aichat.modules.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 后台模型供应商状态更新请求。
 */
@Data
public class AdminModelProviderStatusUpdateRequest {

    /** 目标供应商 ID。 */
    @NotNull(message = "providerId must not be null")
    private Long providerId;

    /** 目标状态。 */
    @NotNull(message = "status must not be null")
    private Integer status;
}
