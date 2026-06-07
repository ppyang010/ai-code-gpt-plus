package com.example.aichat.modules.admin.service.impl;

import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.modules.admin.dto.AdminLoginRequest;
import com.example.aichat.modules.admin.entity.AdminLoginLogDO;
import com.example.aichat.modules.admin.entity.AdminUserDO;
import com.example.aichat.modules.admin.mapper.AdminLoginLogMapper;
import com.example.aichat.modules.admin.mapper.AdminUserMapper;
import com.example.aichat.modules.admin.service.AdminAuthService;
import com.example.aichat.modules.admin.service.AdminPasswordService;
import com.example.aichat.modules.admin.service.AdminTokenService;
import com.example.aichat.modules.admin.support.AdminAuthContext;
import com.example.aichat.modules.admin.vo.AdminAuthInfoVO;
import java.time.LocalDateTime;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 后台鉴权服务实现。
 */
@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthServiceImpl.class);
    private static final int ADMIN_STATUS_ACTIVE = 1;
    private static final int LOGIN_SUCCESS = 1;
    private static final int LOGIN_FAILED = 0;

    private final AdminUserMapper adminUserMapper;
    private final AdminLoginLogMapper adminLoginLogMapper;
    private final AdminPasswordService adminPasswordService;
    private final AdminTokenService adminTokenService;

    public AdminAuthServiceImpl(
            AdminUserMapper adminUserMapper,
            AdminLoginLogMapper adminLoginLogMapper,
            AdminPasswordService adminPasswordService,
            AdminTokenService adminTokenService
    ) {
        this.adminUserMapper = adminUserMapper;
        this.adminLoginLogMapper = adminLoginLogMapper;
        this.adminPasswordService = adminPasswordService;
        this.adminTokenService = adminTokenService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminAuthInfoVO login(AdminLoginRequest request, String loginIp, String userAgent) {
        String normalizedUsername = request.getUsername().trim();
        AdminUserDO adminUser = this.adminUserMapper.selectByUsername(normalizedUsername);
        if (adminUser == null) {
            this.recordLogin(null, normalizedUsername, LOGIN_FAILED, "ADMIN_USER_NOT_FOUND", loginIp, userAgent);
            throw new BizException(ErrorCode.ADMIN_LOGIN_INVALID);
        }
        if (!Objects.equals(adminUser.getStatus(), ADMIN_STATUS_ACTIVE)) {
            this.recordLogin(adminUser.getId(), normalizedUsername, LOGIN_FAILED, "ADMIN_USER_DISABLED", loginIp, userAgent);
            throw new BizException(ErrorCode.ADMIN_LOGIN_INVALID);
        }
        if (!this.adminPasswordService.matches(request.getPassword(), adminUser.getPasswordHash())) {
            this.recordLogin(adminUser.getId(), normalizedUsername, LOGIN_FAILED, "ADMIN_PASSWORD_MISMATCH", loginIp, userAgent);
            throw new BizException(ErrorCode.ADMIN_LOGIN_INVALID);
        }

        LocalDateTime now = LocalDateTime.now();
        this.adminUserMapper.updateLastLoginAt(adminUser.getId(), now);
        adminUser.setLastLoginAt(now);
        String token = this.adminTokenService.createToken(adminUser.getId());
        this.recordLogin(adminUser.getId(), normalizedUsername, LOGIN_SUCCESS, null, loginIp, userAgent);
        log.info("Admin user login success adminUserId={} username={} loginIp={}", adminUser.getId(), normalizedUsername, loginIp);
        return this.toAuthInfo(adminUser, token);
    }

    @Override
    public void logout(AdminAuthContext authContext) {
        // 当前采用无状态 HMAC token，登出阶段不维护服务端会话，只由前端清理本地 token。
        log.info("Admin user logout adminUserId={}", authContext.getAdminUserId());
    }

    @Override
    public AdminAuthInfoVO getCurrentAdminAuthInfo(AdminAuthContext authContext) {
        AdminUserDO adminUser = this.requireActiveAdminUser(authContext.getAdminUserId());
        return this.toAuthInfo(adminUser, authContext.getToken());
    }

    /**
     * 统一查询状态正常的后台管理员，避免登录后管理员被停用仍继续访问后台接口。
     */
    private AdminUserDO requireActiveAdminUser(Long adminUserId) {
        AdminUserDO adminUser = this.adminUserMapper.selectActiveById(adminUserId);
        if (adminUser == null) {
            throw new BizException(ErrorCode.ADMIN_AUTH_REQUIRED);
        }
        return adminUser;
    }

    /**
     * 记录后台登录日志，成功和失败场景都统一落表，便于后续审计和排障。
     */
    private void recordLogin(
            Long adminUserId,
            String loginUsername,
            int successFlag,
            String failureReason,
            String loginIp,
            String userAgent
    ) {
        AdminLoginLogDO loginLog = new AdminLoginLogDO();
        loginLog.setAdminUserId(adminUserId);
        loginLog.setLoginUsername(loginUsername);
        loginLog.setSuccessFlag(successFlag);
        loginLog.setFailureReason(failureReason);
        loginLog.setLoginIp(loginIp);
        loginLog.setUserAgent(userAgent);
        loginLog.setLoginAt(LocalDateTime.now());
        this.adminLoginLogMapper.insert(loginLog);
    }

    /**
     * 将后台管理员实体转换为统一鉴权信息响应，供登录成功和 me 查询共用。
     */
    private AdminAuthInfoVO toAuthInfo(AdminUserDO adminUser, String token) {
        AdminAuthInfoVO authInfo = new AdminAuthInfoVO();
        authInfo.setAdminUserId(adminUser.getId());
        authInfo.setUsername(adminUser.getUsername());
        authInfo.setNickname(adminUser.getNickname());
        authInfo.setEmail(adminUser.getEmail());
        authInfo.setMobile(adminUser.getMobile());
        authInfo.setStatus(adminUser.getStatus());
        authInfo.setLastLoginAt(adminUser.getLastLoginAt());
        authInfo.setToken(token);
        authInfo.setTokenType("Bearer");
        return authInfo;
    }
}
