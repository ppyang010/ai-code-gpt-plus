package com.example.aichat.modules.admin.service;

import com.example.aichat.modules.admin.dto.AdminUsageDailyQuery;
import com.example.aichat.modules.admin.vo.AdminUsageDailyItemVO;
import com.example.aichat.modules.admin.vo.AdminUserUsageSummaryVO;
import java.util.List;

/**
 * 后台用量统计服务。
 */
public interface AdminUsageService {

    /**
     * 查询按日聚合的用量统计。
     */
    List<AdminUsageDailyItemVO> listDailyUsage(AdminUsageDailyQuery query);

    /**
     * 查询单个普通用户的用量汇总。
     */
    AdminUserUsageSummaryVO getUserUsageSummary(Long userId);
}
