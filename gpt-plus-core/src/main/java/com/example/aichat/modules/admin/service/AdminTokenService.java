package com.example.aichat.modules.admin.service;

/**
 * 后台 token 服务。
 */
public interface AdminTokenService {

    /**
     * 为指定后台管理员签发 token。
     */
    String createToken(Long adminUserId);

    /**
     * 校验并解析后台 token。
     */
    AdminTokenPayload verifyToken(String token);
}
