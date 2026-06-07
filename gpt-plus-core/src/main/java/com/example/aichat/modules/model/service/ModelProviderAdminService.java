package com.example.aichat.modules.model.service;

import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.modules.model.dto.AdminModelProviderPageQuery;
import com.example.aichat.modules.model.dto.AdminModelProviderSaveRequest;
import com.example.aichat.modules.model.dto.AdminModelProviderStatusUpdateRequest;
import com.example.aichat.modules.model.vo.AdminModelProviderVO;

/**
 * 后台模型供应商管理服务。
 */
public interface ModelProviderAdminService {

    /**
     * 分页查询模型供应商。
     */
    PageResponse<AdminModelProviderVO> pageProviders(AdminModelProviderPageQuery query);

    /**
     * 查询单个模型供应商详情。
     */
    AdminModelProviderVO getProviderDetail(Long providerId);

    /**
     * 新增模型供应商。
     */
    void createProvider(AdminModelProviderSaveRequest request);

    /**
     * 编辑模型供应商。
     */
    void updateProvider(AdminModelProviderSaveRequest request);

    /**
     * 更新模型供应商状态。
     */
    void updateProviderStatus(AdminModelProviderStatusUpdateRequest request);
}
