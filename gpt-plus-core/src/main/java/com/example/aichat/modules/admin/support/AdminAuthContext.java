package com.example.aichat.modules.admin.support;

/**
 * 单次请求内的后台管理员上下文。
 */
public class AdminAuthContext {

    /** 当前请求对应的后台管理员 ID。 */
    private final Long adminUserId;
    /** 当前请求使用的后台 Bearer token。 */
    private final String token;

    public AdminAuthContext(Long adminUserId, String token) {
        this.adminUserId = adminUserId;
        this.token = token;
    }

    public Long getAdminUserId() {
        return adminUserId;
    }

    public String getToken() {
        return token;
    }
}
