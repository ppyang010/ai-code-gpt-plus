package com.example.aichat.modules.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 普通用户状态更新请求。
 */
@Data
public class AppUserStatusUpdateRequest {

    /** 目标普通用户 ID。 */
    @NotNull(message = "userId must not be null")
    private Long userId;

    /** 目标状态。 */
    @NotNull(message = "status must not be null")
    private Integer status;
}
