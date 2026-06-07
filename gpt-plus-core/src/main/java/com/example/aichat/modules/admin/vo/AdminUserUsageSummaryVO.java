package com.example.aichat.modules.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 单个普通用户的用量汇总响应对象。
 */
@Data
public class AdminUserUsageSummaryVO {

    /** 普通用户 ID。 */
    private Long userId;

    /** 输入 token 总数。 */
    private Integer totalPromptTokens;

    /** 输出 token 总数。 */
    private Integer totalCompletionTokens;

    /** 总 token 数。 */
    private Integer totalTokens;

    /** 累计预估成本。 */
    private BigDecimal estimatedCost;

    /** 最近一次请求时间。 */
    private LocalDateTime lastRequestAt;
}
