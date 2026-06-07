package com.example.aichat.modules.admin.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 后台登录成功后返回的管理员鉴权信息。
 */
@Data
public class AdminAuthInfoVO {

    /** 当前登录管理员 ID。 */
    private Long adminUserId;

    /** 当前登录管理员用户名。 */
    private String username;

    /** 当前登录管理员昵称。 */
    private String nickname;

    /** 当前登录管理员邮箱。 */
    private String email;

    /** 当前登录管理员手机号。 */
    private String mobile;

    /** 当前登录管理员状态。 */
    private Integer status;

    /** 当前登录管理员最近一次登录时间。 */
    private LocalDateTime lastLoginAt;

    /** 后台访问令牌。 */
    private String token;

    /** 后台访问令牌类型，首版默认预留为 Bearer。 */
    private String tokenType;
}
