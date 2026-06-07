package com.example.aichat.modules.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 后台管理员登录日志实体。
 */
@Data
@TableName("admin_login_log")
public class AdminLoginLogDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 登录账号。 */
    @TableField("login_username")
    private String loginUsername;

    /** 管理员 ID；失败场景允许为空。 */
    @TableField("admin_user_id")
    private Long adminUserId;

    /** 是否成功：1 成功，0 失败。 */
    @TableField("success_flag")
    private Integer successFlag;

    /** 登录失败原因。 */
    @TableField("failure_reason")
    private String failureReason;

    /** 登录 IP。 */
    @TableField("login_ip")
    private String loginIp;

    /** 登录时的 User-Agent。 */
    @TableField("user_agent")
    private String userAgent;

    /** 登录时间。 */
    @TableField("login_at")
    private LocalDateTime loginAt;

    /** 创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
