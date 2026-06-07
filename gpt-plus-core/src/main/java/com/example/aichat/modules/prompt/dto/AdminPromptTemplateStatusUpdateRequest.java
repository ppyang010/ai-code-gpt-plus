package com.example.aichat.modules.prompt.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 后台提示词模板状态更新请求。
 */
@Data
public class AdminPromptTemplateStatusUpdateRequest {

    /** 目标模板 ID。 */
    @NotNull(message = "templateId must not be null")
    private Long templateId;

    /** 目标状态。 */
    @NotNull(message = "status must not be null")
    private Integer status;
}
