package com.example.aichat.modules.admin.controller;

import com.example.aichat.common.dto.CommonResponse;
import com.example.aichat.modules.admin.dto.AdminUsageDailyQuery;
import com.example.aichat.modules.admin.service.AdminUsageService;
import com.example.aichat.modules.admin.vo.AdminUsageDailyItemVO;
import com.example.aichat.modules.admin.vo.AdminUserUsageSummaryVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台用量统计控制器。
 */
@RestController
@RequestMapping("/api/admin/usage")
@Validated
public class AdminUsageController {

    /** 后台用量统计服务。 */
    private final AdminUsageService adminUsageService;

    public AdminUsageController(AdminUsageService adminUsageService) {
        this.adminUsageService = adminUsageService;
    }

    /**
     * 查询按日聚合的用量统计。
     *
     * @param query 查询条件
     * @return 每日用量统计列表
     */
    @GetMapping("/daily")
    public CommonResponse<List<AdminUsageDailyItemVO>> daily(@Valid AdminUsageDailyQuery query) {
        return CommonResponse.success(this.adminUsageService.listDailyUsage(query));
    }

    /**
     * 查询单个普通用户的用量汇总。
     *
     * @param userId 普通用户 ID
     * @return 单个用户的用量汇总
     */
    @GetMapping("/users/{userId}/summary")
    public CommonResponse<AdminUserUsageSummaryVO> userSummary(
            @Positive(message = "userId must be greater than 0") @PathVariable("userId") Long userId
    ) {
        return CommonResponse.success(this.adminUsageService.getUserUsageSummary(userId));
    }
}
