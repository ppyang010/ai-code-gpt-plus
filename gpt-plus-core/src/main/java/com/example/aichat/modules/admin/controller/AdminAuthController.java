package com.example.aichat.modules.admin.controller;

import com.example.aichat.common.dto.CommonResponse;
import com.example.aichat.modules.admin.dto.AdminLoginRequest;
import com.example.aichat.modules.admin.service.AdminAuthService;
import com.example.aichat.modules.admin.support.AdminAuthContextHolder;
import com.example.aichat.modules.admin.vo.AdminAuthInfoVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台鉴权控制器。
 *
 * <p>当前已接入管理员登录、登出和当前管理员信息查询，
 * 负责串起密码校验、token 签发、登录日志写入和请求上下文读取。</p>
 */
@RestController
@RequestMapping("/api/admin/auth")
@Validated
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    /**
     * 后台管理员登录接口契约。
     *
     * @param request 管理员登录请求
     * @param httpServletRequest 当前 HTTP 请求，用于提取登录 IP 和 User-Agent
     * @return 登录成功后的管理员鉴权信息
     */
    @PostMapping("/login")
    public CommonResponse<AdminAuthInfoVO> login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return CommonResponse.success(this.adminAuthService.login(
                request,
                this.resolveLoginIp(httpServletRequest),
                this.resolveUserAgent(httpServletRequest)
        ));
    }

    /**
     * 后台管理员登出接口契约。
     *
     * @return 是否登出成功
     */
    @PostMapping("/logout")
    public CommonResponse<Boolean> logout() {
        this.adminAuthService.logout(AdminAuthContextHolder.getRequiredContext());
        return CommonResponse.success(Boolean.TRUE);
    }

    /**
     * 获取当前后台管理员信息接口契约。
     *
     * @return 当前管理员鉴权信息
     */
    @GetMapping("/me")
    public CommonResponse<AdminAuthInfoVO> me() {
        return CommonResponse.success(this.adminAuthService.getCurrentAdminAuthInfo(AdminAuthContextHolder.getRequiredContext()));
    }

    /**
     * 优先取代理层透传的真实客户端 IP，没有代理头时回退到 servlet remoteAddr。
     */
    private String resolveLoginIp(HttpServletRequest httpServletRequest) {
        String forwardedFor = httpServletRequest == null ? null : httpServletRequest.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return httpServletRequest == null ? null : httpServletRequest.getRemoteAddr();
    }

    /**
     * 提取当前请求的 User-Agent，供后台登录日志落库。
     */
    private String resolveUserAgent(HttpServletRequest httpServletRequest) {
        return httpServletRequest == null ? null : httpServletRequest.getHeader("User-Agent");
    }
}
