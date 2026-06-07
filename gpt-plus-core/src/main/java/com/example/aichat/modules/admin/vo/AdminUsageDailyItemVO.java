package com.example.aichat.modules.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/**
 * 后台用量日报聚合响应对象。
 */
@Data
public class AdminUsageDailyItemVO {

    /** 统计日期。 */
    private LocalDate statDate;

    /** 当日活跃用户数。 */
    private Integer userCount;

    /** 当日请求数。 */
    private Integer requestCount;

    /** 当日输入 token 总数。 */
    private Integer promptTokens;

    /** 当日输出 token 总数。 */
    private Integer completionTokens;

    /** 当日总 token 数。 */
    private Integer totalTokens;

    /** 当日预估成本。 */
    private BigDecimal estimatedCost;
}
