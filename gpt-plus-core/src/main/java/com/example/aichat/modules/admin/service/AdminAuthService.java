package com.example.aichat.modules.admin.service;

import com.example.aichat.modules.admin.dto.AdminLoginRequest;
import com.example.aichat.modules.admin.support.AdminAuthContext;
import com.example.aichat.modules.admin.vo.AdminAuthInfoVO;

/**
 * 后台鉴权服务。
 */
public interface AdminAuthService {

    /**
     * 执行后台管理员登录。
     */
    AdminAuthInfoVO login(AdminLoginRequest request, String loginIp, String userAgent);

    /**
     * 执行后台管理员登出。
     */
    void logout(AdminAuthContext authContext);

    /**
     * 获取当前后台管理员信息。
     */
    AdminAuthInfoVO getCurrentAdminAuthInfo(AdminAuthContext authContext);
}
