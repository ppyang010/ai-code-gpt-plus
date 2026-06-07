package com.example.aichat.modules.model.controller.admin;

import com.example.aichat.common.dto.CommonResponse;
import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.modules.model.dto.AdminModelProviderPageQuery;
import com.example.aichat.modules.model.dto.AdminModelProviderSaveRequest;
import com.example.aichat.modules.model.dto.AdminModelProviderStatusUpdateRequest;
import com.example.aichat.modules.model.service.ModelProviderAdminService;
import com.example.aichat.modules.model.vo.AdminModelProviderVO;
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
 * 后台模型供应商管理控制器。
 */
@RestController
@RequestMapping("/api/admin/model-providers")
@Validated
public class AdminModelProviderController {

    /** 后台模型供应商服务。 */
    private final ModelProviderAdminService modelProviderAdminService;

    public AdminModelProviderController(ModelProviderAdminService modelProviderAdminService) {
        this.modelProviderAdminService = modelProviderAdminService;
    }

    /**
     * 分页查询模型供应商。
     *
     * @param query 查询条件
     * @return 分页后的模型供应商列表
     */
    @GetMapping("/page")
    public CommonResponse<PageResponse<AdminModelProviderVO>> page(@Valid AdminModelProviderPageQuery query) {
        return CommonResponse.success(this.modelProviderAdminService.pageProviders(query));
    }

    /**
     * 查询单个模型供应商详情。
     *
     * @param providerId 供应商 ID
     * @return 供应商详情
     */
    @GetMapping("/{providerId}")
    public CommonResponse<AdminModelProviderVO> detail(
            @Positive(message = "providerId must be greater than 0") @PathVariable("providerId") Long providerId
    ) {
        return CommonResponse.success(this.modelProviderAdminService.getProviderDetail(providerId));
    }

    /**
     * 新增模型供应商。
     *
     * @param request 供应商新增请求
     * @return 是否新增成功
     */
    @PostMapping("/create")
    public CommonResponse<Boolean> create(@Valid @RequestBody AdminModelProviderSaveRequest request) {
        this.modelProviderAdminService.createProvider(request);
        return CommonResponse.success(Boolean.TRUE);
    }

    /**
     * 编辑模型供应商。
     *
     * @param request 供应商编辑请求
     * @return 是否编辑成功
     */
    @PostMapping("/update")
    public CommonResponse<Boolean> update(@Valid @RequestBody AdminModelProviderSaveRequest request) {
        this.modelProviderAdminService.updateProvider(request);
        return CommonResponse.success(Boolean.TRUE);
    }

    /**
     * 更新模型供应商状态。
     *
     * @param request 状态更新请求
     * @return 是否更新成功
     */
    @PostMapping("/update-status")
    public CommonResponse<Boolean> updateStatus(@Valid @RequestBody AdminModelProviderStatusUpdateRequest request) {
        this.modelProviderAdminService.updateProviderStatus(request);
        return CommonResponse.success(Boolean.TRUE);
    }
}
