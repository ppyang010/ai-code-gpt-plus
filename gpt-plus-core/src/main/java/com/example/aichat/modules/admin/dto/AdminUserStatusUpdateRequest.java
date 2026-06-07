package com.example.aichat.modules.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 后台管理员状态更新请求。
 */
@Data
public class AdminUserStatusUpdateRequest {

    /** 目标管理员 ID。 */
    @NotNull(message = "adminUserId must not be null")
    private Long adminUserId;

    /** 目标状态。 */
    @NotNull(message = "status must not be null")
    private Integer status;
}
