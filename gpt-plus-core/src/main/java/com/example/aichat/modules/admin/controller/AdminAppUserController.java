package com.example.aichat.modules.admin.controller;

import com.example.aichat.common.dto.CommonResponse;
import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.modules.admin.dto.AppUserPageQuery;
import com.example.aichat.modules.admin.dto.AppUserStatusUpdateRequest;
import com.example.aichat.modules.admin.service.AdminAppUserService;
import com.example.aichat.modules.admin.vo.AppUserAdminItemVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 普通用户后台管理控制器。
 */
@RestController
@RequestMapping("/api/admin/app-users")
@Validated
public class AdminAppUserController {

    /** 普通用户后台管理服务。 */
    private final AdminAppUserService adminAppUserService;

    public AdminAppUserController(AdminAppUserService adminAppUserService) {
        this.adminAppUserService = adminAppUserService;
    }

    /**
     * 分页查询普通用户。
     *
     * @param query 查询条件
     * @return 分页后的普通用户列表
     */
    @GetMapping("/page")
    public CommonResponse<PageResponse<AppUserAdminItemVO>> page(@Valid AppUserPageQuery query) {
        return CommonResponse.success(this.adminAppUserService.pageAppUsers(query));
    }

    /**
     * 查询普通用户详情。
     *
     * @param userId 普通用户 ID
     * @return 普通用户详情
     */
    @GetMapping("/{userId}")
    public CommonResponse<AppUserAdminItemVO> detail(
            @Positive(message = "userId must be greater than 0") @PathVariable("userId") Long userId
    ) {
        return CommonResponse.success(this.adminAppUserService.getAppUserDetail(userId));
    }

    /**
     * 更新普通用户状态。
     *
     * @param request 状态更新请求
     * @return 是否更新成功
     */
    @PostMapping("/update-status")
    public CommonResponse<Boolean> updateStatus(@Valid @RequestBody AppUserStatusUpdateRequest request) {
        this.adminAppUserService.updateAppUserStatus(request);
        return CommonResponse.success(Boolean.TRUE);
    }
}
