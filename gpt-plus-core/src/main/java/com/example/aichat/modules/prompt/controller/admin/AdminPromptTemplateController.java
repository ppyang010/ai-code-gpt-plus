package com.example.aichat.modules.prompt.controller.admin;

import com.example.aichat.common.dto.CommonResponse;
import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.modules.prompt.dto.AdminPromptTemplatePageQuery;
import com.example.aichat.modules.prompt.dto.AdminPromptTemplateSaveRequest;
import com.example.aichat.modules.prompt.dto.AdminPromptTemplateStatusUpdateRequest;
import com.example.aichat.modules.prompt.service.PromptTemplateService;
import com.example.aichat.modules.prompt.vo.PromptTemplateVO;
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
 * 后台提示词模板管理控制器。
 */
@RestController
@RequestMapping("/api/admin/prompt-templates")
@Validated
public class AdminPromptTemplateController {

    /** 提示词模板服务。 */
    private final PromptTemplateService promptTemplateService;

    public AdminPromptTemplateController(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    /**
     * 分页查询提示词模板。
     *
     * @param query 查询条件
     * @return 分页后的模板列表
     */
    @GetMapping("/page")
    public CommonResponse<PageResponse<PromptTemplateVO>> page(@Valid AdminPromptTemplatePageQuery query) {
        return CommonResponse.success(this.promptTemplateService.pageTemplates(query));
    }

    /**
     * 查询单个提示词模板详情。
     *
     * @param templateId 模板 ID
     * @return 模板详情
     */
    @GetMapping("/{templateId}")
    public CommonResponse<PromptTemplateVO> detail(
            @Positive(message = "templateId must be greater than 0") @PathVariable("templateId") Long templateId
    ) {
        return CommonResponse.success(this.promptTemplateService.getTemplateDetail(templateId));
    }

    /**
     * 新增提示词模板。
     *
     * @param request 模板新增请求
     * @return 是否新增成功
     */
    @PostMapping("/create")
    public CommonResponse<Boolean> create(@Valid @RequestBody AdminPromptTemplateSaveRequest request) {
        this.promptTemplateService.createTemplate(request);
        return CommonResponse.success(Boolean.TRUE);
    }

    /**
     * 编辑提示词模板。
     *
     * @param request 模板编辑请求
     * @return 是否编辑成功
     */
    @PostMapping("/update")
    public CommonResponse<Boolean> update(@Valid @RequestBody AdminPromptTemplateSaveRequest request) {
        this.promptTemplateService.updateTemplate(request);
        return CommonResponse.success(Boolean.TRUE);
    }

    /**
     * 更新提示词模板状态。
     *
     * @param request 状态更新请求
     * @return 是否更新成功
     */
    @PostMapping("/update-status")
    public CommonResponse<Boolean> updateStatus(@Valid @RequestBody AdminPromptTemplateStatusUpdateRequest request) {
        this.promptTemplateService.updateTemplateStatus(request);
        return CommonResponse.success(Boolean.TRUE);
    }
}
