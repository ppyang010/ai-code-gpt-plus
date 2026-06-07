package com.example.aichat.modules.admin.controller;

import com.example.aichat.common.dto.CommonResponse;
import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.modules.admin.dto.AdminUserPageQuery;
import com.example.aichat.modules.admin.dto.AdminUserResetPasswordRequest;
import com.example.aichat.modules.admin.dto.AdminUserSaveRequest;
import com.example.aichat.modules.admin.dto.AdminUserStatusUpdateRequest;
import com.example.aichat.modules.admin.service.AdminUserService;
import com.example.aichat.modules.admin.vo.AdminUserItemVO;
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
 * 后台管理员管理控制器。
 */
@RestController
@RequestMapping("/api/admin/admin-users")
@Validated
public class AdminUserController {

    /** 后台管理员管理服务。 */
    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * 分页查询后台管理员。
     *
     * @param query 查询条件
     * @return 分页后的管理员列表
     */
    @GetMapping("/page")
    public CommonResponse<PageResponse<AdminUserItemVO>> page(@Valid AdminUserPageQuery query) {
        return CommonResponse.success(this.adminUserService.pageAdminUsers(query));
    }

    /**
     * 查询后台管理员详情。
     *
     * @param adminUserId 管理员 ID
     * @return 管理员详情
     */
    @GetMapping("/{adminUserId}")
    public CommonResponse<AdminUserItemVO> detail(
            @Positive(message = "adminUserId must be greater than 0") @PathVariable("adminUserId") Long adminUserId
    ) {
        return CommonResponse.success(this.adminUserService.getAdminUserDetail(adminUserId));
    }

    /**
     * 新增后台管理员。
     *
     * @param request 管理员新增请求
     * @return 是否新增成功
     */
    @PostMapping("/create")
    public CommonResponse<Boolean> create(@Valid @RequestBody AdminUserSaveRequest request) {
        this.adminUserService.createAdminUser(request);
        return CommonResponse.success(Boolean.TRUE);
    }

    /**
     * 编辑后台管理员。
     *
     * @param request 管理员编辑请求
     * @return 是否编辑成功
     */
    @PostMapping("/update")
    public CommonResponse<Boolean> update(@Valid @RequestBody AdminUserSaveRequest request) {
        this.adminUserService.updateAdminUser(request);
        return CommonResponse.success(Boolean.TRUE);
    }

    /**
     * 更新后台管理员状态。
     *
     * @param request 状态更新请求
     * @return 是否更新成功
     */
    @PostMapping("/update-status")
    public CommonResponse<Boolean> updateStatus(@Valid @RequestBody AdminUserStatusUpdateRequest request) {
        this.adminUserService.updateAdminUserStatus(request);
        return CommonResponse.success(Boolean.TRUE);
    }

    /**
     * 重置后台管理员密码。
     *
     * @param request 密码重置请求
     * @return 是否重置成功
     */
    @PostMapping("/reset-password")
    public CommonResponse<Boolean> resetPassword(@Valid @RequestBody AdminUserResetPasswordRequest request) {
        this.adminUserService.resetAdminUserPassword(request);
        return CommonResponse.success(Boolean.TRUE);
    }
}
