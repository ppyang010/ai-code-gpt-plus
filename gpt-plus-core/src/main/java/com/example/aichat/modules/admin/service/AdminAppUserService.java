package com.example.aichat.modules.admin.service;

import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.modules.admin.dto.AppUserPageQuery;
import com.example.aichat.modules.admin.dto.AppUserStatusUpdateRequest;
import com.example.aichat.modules.admin.vo.AppUserAdminItemVO;

/**
 * 普通用户后台管理服务。
 */
public interface AdminAppUserService {

    /**
     * 分页查询普通用户。
     */
    PageResponse<AppUserAdminItemVO> pageAppUsers(AppUserPageQuery query);

    /**
     * 查询普通用户详情。
     */
    AppUserAdminItemVO getAppUserDetail(Long userId);

    /**
     * 更新普通用户状态。
     */
    void updateAppUserStatus(AppUserStatusUpdateRequest request);
}
