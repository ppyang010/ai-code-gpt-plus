package com.example.aichat.modules.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.model.BaseDO;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app_user")
public class AppUserDO extends BaseDO {

    @TableField("username")
    private String username;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("nickname")
    private String nickname;

    @TableField("avatar_url")
    private String avatarUrl;

    @TableField("email")
    private String email;

    @TableField("mobile")
    private String mobile;

    @TableField("status")
    private Integer status;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;
}
