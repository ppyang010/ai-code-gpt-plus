package com.example.aichat.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台管理员登录请求。
 */
@Data
public class AdminLoginRequest {

    /** 管理员登录用户名。 */
    @NotBlank(message = "username must not be blank")
    @Size(max = 64, message = "username length must be at most 64")
    private String username;

    /** 管理员登录密码明文，后续在服务层完成哈希校验。 */
    @NotBlank(message = "password must not be blank")
    @Size(max = 128, message = "password length must be at most 128")
    private String password;
}
