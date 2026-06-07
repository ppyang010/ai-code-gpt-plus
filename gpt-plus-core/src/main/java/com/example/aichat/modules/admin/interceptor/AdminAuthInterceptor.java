package com.example.aichat.modules.admin.interceptor;

import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.modules.admin.entity.AdminUserDO;
import com.example.aichat.modules.admin.mapper.AdminUserMapper;
import com.example.aichat.modules.admin.service.AdminTokenPayload;
import com.example.aichat.modules.admin.service.AdminTokenService;
import com.example.aichat.modules.admin.support.AdminAuthContext;
import com.example.aichat.modules.admin.support.AdminAuthContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 后台接口鉴权拦截器。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final int ADMIN_STATUS_ACTIVE = 1;

    private final AdminTokenService adminTokenService;
    private final AdminUserMapper adminUserMapper;

    public AdminAuthInterceptor(AdminTokenService adminTokenService, AdminUserMapper adminUserMapper) {
        this.adminTokenService = adminTokenService;
        this.adminUserMapper = adminUserMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String bearerToken = this.extractBearerToken(request);
        AdminTokenPayload tokenPayload = this.adminTokenService.verifyToken(bearerToken);
        AdminUserDO adminUser = this.adminUserMapper.selectById(tokenPayload.getAdminUserId());
        if (adminUser == null || !Objects.equals(adminUser.getStatus(), ADMIN_STATUS_ACTIVE)) {
            throw new BizException(ErrorCode.ADMIN_AUTH_REQUIRED);
        }
        AdminAuthContextHolder.setContext(new AdminAuthContext(adminUser.getId(), bearerToken));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AdminAuthContextHolder.clear();
    }

    /**
     * 统一从 Authorization 头里提取 Bearer token，不接受其他 header 变体，减少后台鉴权歧义。
     */
    private String extractBearerToken(HttpServletRequest request) {
        String authorizationHeader = request == null ? null : request.getHeader("Authorization");
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new BizException(ErrorCode.ADMIN_AUTH_REQUIRED);
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new BizException(ErrorCode.ADMIN_AUTH_REQUIRED);
        }
        return token;
    }
}
