package com.example.aichat.modules.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 普通用户后台管理响应对象。
 */
@Data
public class AppUserAdminItemVO {

    /** 普通用户 ID。 */
    private Long userId;

    /** 普通用户名。 */
    private String username;

    /** 普通用户昵称。 */
    private String nickname;

    /** 普通用户邮箱。 */
    private String email;

    /** 普通用户手机号。 */
    private String mobile;

    /** 普通用户状态。 */
    private Integer status;

    /** 当前余额，首版只读展示。 */
    private BigDecimal balanceAmount;

    /** 最近一次登录时间。 */
    private LocalDateTime lastLoginAt;

    /** 最近一次请求时间。 */
    private LocalDateTime lastRequestAt;

    /** 累计总 token，用于详情页展示基础用量概览。 */
    private Long totalTokens;

    /** 累计预估成本，用于详情页展示基础成本概览。 */
    private BigDecimal estimatedCost;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
