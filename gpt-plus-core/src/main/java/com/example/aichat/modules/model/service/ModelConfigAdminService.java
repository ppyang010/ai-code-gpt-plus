package com.example.aichat.modules.model.service;

import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.modules.model.dto.AdminModelConfigPageQuery;
import com.example.aichat.modules.model.dto.AdminModelConfigSaveRequest;
import com.example.aichat.modules.model.dto.AdminModelConfigStatusUpdateRequest;
import com.example.aichat.modules.model.vo.AdminModelConfigVO;

/**
 * 后台模型配置管理服务。
 */
public interface ModelConfigAdminService {

    /**
     * 分页查询模型配置。
     */
    PageResponse<AdminModelConfigVO> pageConfigs(AdminModelConfigPageQuery query);

    /**
     * 查询单个模型配置详情。
     */
    AdminModelConfigVO getConfigDetail(Long modelId);

    /**
     * 新增模型配置。
     */
    void createConfig(AdminModelConfigSaveRequest request);

    /**
     * 编辑模型配置。
     */
    void updateConfig(AdminModelConfigSaveRequest request);

    /**
     * 更新模型配置状态。
     */
    void updateConfigStatus(AdminModelConfigStatusUpdateRequest request);
}
