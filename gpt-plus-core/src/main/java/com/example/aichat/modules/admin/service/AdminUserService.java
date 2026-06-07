package com.example.aichat.modules.admin.service;

import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.modules.admin.dto.AdminUserPageQuery;
import com.example.aichat.modules.admin.dto.AdminUserResetPasswordRequest;
import com.example.aichat.modules.admin.dto.AdminUserSaveRequest;
import com.example.aichat.modules.admin.dto.AdminUserStatusUpdateRequest;
import com.example.aichat.modules.admin.vo.AdminUserItemVO;

/**
 * 后台管理员管理服务。
 */
public interface AdminUserService {

    /**
     * 分页查询后台管理员。
     */
    PageResponse<AdminUserItemVO> pageAdminUsers(AdminUserPageQuery query);

    /**
     * 查询后台管理员详情。
     */
    AdminUserItemVO getAdminUserDetail(Long adminUserId);

    /**
     * 新增后台管理员。
     */
    void createAdminUser(AdminUserSaveRequest request);

    /**
     * 编辑后台管理员。
     */
    void updateAdminUser(AdminUserSaveRequest request);

    /**
     * 更新后台管理员状态。
     */
    void updateAdminUserStatus(AdminUserStatusUpdateRequest request);

    /**
     * 重置后台管理员密码。
     */
    void resetAdminUserPassword(AdminUserResetPasswordRequest request);
}
