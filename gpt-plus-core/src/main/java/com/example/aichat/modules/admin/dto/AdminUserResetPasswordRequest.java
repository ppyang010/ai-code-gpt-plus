package com.example.aichat.modules.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台管理员重置密码请求。
 */
@Data
public class AdminUserResetPasswordRequest {

    /** 目标管理员 ID。 */
    @NotNull(message = "adminUserId must not be null")
    private Long adminUserId;

    /** 新密码明文，后续由服务层完成哈希处理。 */
    @Size(max = 128, message = "newPassword length must be at most 128")
    private String newPassword;
}
