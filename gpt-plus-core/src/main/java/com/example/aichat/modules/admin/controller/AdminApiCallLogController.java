package com.example.aichat.modules.admin.controller;

import com.example.aichat.common.dto.CommonResponse;
import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.modules.admin.dto.AdminApiCallLogPageQuery;
import com.example.aichat.modules.admin.service.AdminApiCallLogService;
import com.example.aichat.modules.admin.vo.AdminApiCallLogItemVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台模型调用日志控制器。
 */
@RestController
@RequestMapping("/api/admin/api-call-logs")
@Validated
public class AdminApiCallLogController {

    /** 后台模型调用日志服务。 */
    private final AdminApiCallLogService adminApiCallLogService;

    public AdminApiCallLogController(AdminApiCallLogService adminApiCallLogService) {
        this.adminApiCallLogService = adminApiCallLogService;
    }

    /**
     * 分页查询模型调用日志。
     *
     * @param query 查询条件
     * @return 分页后的日志列表
     */
    @GetMapping("/page")
    public CommonResponse<PageResponse<AdminApiCallLogItemVO>> page(@Valid AdminApiCallLogPageQuery query) {
        return CommonResponse.success(this.adminApiCallLogService.pageLogs(query));
    }

    /**
     * 查询单条模型调用日志详情。
     *
     * @param logId 调用日志 ID
     * @return 日志详情
     */
    @GetMapping("/{logId}")
    public CommonResponse<AdminApiCallLogItemVO> detail(
            @Positive(message = "logId must be greater than 0") @PathVariable("logId") Long logId
    ) {
        return CommonResponse.success(this.adminApiCallLogService.getLogDetail(logId));
    }
}
