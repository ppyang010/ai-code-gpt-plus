package com.example.aichat.modules.prompt.service;

import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.modules.prompt.dto.AdminPromptTemplatePageQuery;
import com.example.aichat.modules.prompt.dto.AdminPromptTemplateSaveRequest;
import com.example.aichat.modules.prompt.dto.AdminPromptTemplateStatusUpdateRequest;
import com.example.aichat.modules.prompt.vo.PromptTemplateVO;
import java.util.List;

/**
 * 提示词模板服务。
 */
public interface PromptTemplateService {

    /**
     * 分页查询提示词模板。
     */
    PageResponse<PromptTemplateVO> pageTemplates(AdminPromptTemplatePageQuery query);

    /**
     * 查询单个提示词模板详情。
     */
    PromptTemplateVO getTemplateDetail(Long templateId);

    /**
     * 新增提示词模板。
     */
    void createTemplate(AdminPromptTemplateSaveRequest request);

    /**
     * 编辑提示词模板。
     */
    void updateTemplate(AdminPromptTemplateSaveRequest request);

    /**
     * 更新提示词模板状态。
     */
    void updateTemplateStatus(AdminPromptTemplateStatusUpdateRequest request);

    /**
     * 读取当前启用的全局附加提示词模板，供聊天系统提示词拼装使用。
     */
    List<String> listEnabledGlobalPromptContents();
}
