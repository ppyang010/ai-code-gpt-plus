package com.example.aichat.modules.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台管理员新增或编辑请求。
 */
@Data
public class AdminUserSaveRequest {

    /** 管理员 ID；创建时可为空，更新时由后续逻辑校验必填。 */
    private Long adminUserId;

    /** 管理员用户名。 */
    @Size(max = 64, message = "username length must be at most 64")
    private String username;

    /** 管理员登录密码明文；创建或重置时使用，后续由服务层做哈希处理。 */
    @Size(max = 128, message = "password length must be at most 128")
    private String password;

    /** 管理员昵称。 */
    @Size(max = 64, message = "nickname length must be at most 64")
    private String nickname;

    /** 管理员邮箱。 */
    @Email(message = "email format is invalid")
    @Size(max = 128, message = "email length must be at most 128")
    private String email;

    /** 管理员手机号。 */
    @Size(max = 32, message = "mobile length must be at most 32")
    private String mobile;

    /** 管理员状态。 */
    private Integer status;
}
