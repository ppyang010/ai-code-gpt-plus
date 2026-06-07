package com.example.aichat.modules.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台用量日报聚合查询入参。
 */
@Data
public class AdminUsageDailyQuery {

    /** 起始日期，后续统一按 yyyy-MM-dd 解析。 */
    @Size(max = 16, message = "startDate length must be at most 16")
    private String startDate;

    /** 结束日期，后续统一按 yyyy-MM-dd 解析。 */
    @Size(max = 16, message = "endDate length must be at most 16")
    private String endDate;

    /** 按用户 ID 精确筛选。 */
    private Long userId;

    /** 按供应商 ID 精确筛选。 */
    private Long providerId;

    /** 按模型 ID 精确筛选。 */
    private Long modelId;
}
