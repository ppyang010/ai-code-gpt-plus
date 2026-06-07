package com.example.aichat.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.model.BaseDO;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台管理员实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_user")
public class AdminUserDO extends BaseDO {

    /** 管理员用户名。 */
    @TableField("username")
    private String username;

    /** 管理员密码哈希。 */
    @TableField("password_hash")
    private String passwordHash;

    /** 管理员昵称。 */
    @TableField("nickname")
    private String nickname;

    /** 管理员邮箱。 */
    @TableField("email")
    private String email;

    /** 管理员手机号。 */
    @TableField("mobile")
    private String mobile;

    /** 管理员状态。 */
    @TableField("status")
    private Integer status;

    /** 最近一次登录时间。 */
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;
}
