package com.example.aichat.modules.admin.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 后台管理员列表或详情响应对象。
 */
@Data
public class AdminUserItemVO {

    /** 管理员 ID。 */
    private Long adminUserId;

    /** 管理员用户名。 */
    private String username;

    /** 管理员昵称。 */
    private String nickname;

    /** 管理员邮箱。 */
    private String email;

    /** 管理员手机号。 */
    private String mobile;

    /** 管理员状态。 */
    private Integer status;

    /** 最近一次登录时间。 */
    private LocalDateTime lastLoginAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
