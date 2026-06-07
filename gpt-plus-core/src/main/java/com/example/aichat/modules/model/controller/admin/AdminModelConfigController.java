package com.example.aichat.modules.model.controller.admin;

import com.example.aichat.common.dto.CommonResponse;
import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.modules.model.dto.AdminModelConfigPageQuery;
import com.example.aichat.modules.model.dto.AdminModelConfigSaveRequest;
import com.example.aichat.modules.model.dto.AdminModelConfigStatusUpdateRequest;
import com.example.aichat.modules.model.service.ModelConfigAdminService;
import com.example.aichat.modules.model.vo.AdminModelConfigVO;
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
 * 后台模型配置管理控制器。
 */
@RestController
@RequestMapping("/api/admin/model-configs")
@Validated
public class AdminModelConfigController {

    /** 后台模型配置服务。 */
    private final ModelConfigAdminService modelConfigAdminService;

    public AdminModelConfigController(ModelConfigAdminService modelConfigAdminService) {
        this.modelConfigAdminService = modelConfigAdminService;
    }

    /**
     * 分页查询模型配置。
     *
     * @param query 查询条件
     * @return 分页后的模型配置列表
     */
    @GetMapping("/page")
    public CommonResponse<PageResponse<AdminModelConfigVO>> page(@Valid AdminModelConfigPageQuery query) {
        return CommonResponse.success(this.modelConfigAdminService.pageConfigs(query));
    }

    /**
     * 查询单个模型配置详情。
     *
     * @param modelId 模型 ID
     * @return 模型配置详情
     */
    @GetMapping("/{modelId}")
    public CommonResponse<AdminModelConfigVO> detail(
            @Positive(message = "modelId must be greater than 0") @PathVariable("modelId") Long modelId
    ) {
        return CommonResponse.success(this.modelConfigAdminService.getConfigDetail(modelId));
    }

    /**
     * 新增模型配置。
     *
     * @param request 模型配置新增请求
     * @return 是否新增成功
     */
    @PostMapping("/create")
    public CommonResponse<Boolean> create(@Valid @RequestBody AdminModelConfigSaveRequest request) {
        this.modelConfigAdminService.createConfig(request);
        return CommonResponse.success(Boolean.TRUE);
    }

    /**
     * 编辑模型配置。
     *
     * @param request 模型配置编辑请求
     * @return 是否编辑成功
     */
    @PostMapping("/update")
    public CommonResponse<Boolean> update(@Valid @RequestBody AdminModelConfigSaveRequest request) {
        this.modelConfigAdminService.updateConfig(request);
        return CommonResponse.success(Boolean.TRUE);
    }

    /**
     * 更新模型配置状态。
     *
     * @param request 状态更新请求
     * @return 是否更新成功
     */
    @PostMapping("/update-status")
    public CommonResponse<Boolean> updateStatus(@Valid @RequestBody AdminModelConfigStatusUpdateRequest request) {
        this.modelConfigAdminService.updateConfigStatus(request);
        return CommonResponse.success(Boolean.TRUE);
    }
}
