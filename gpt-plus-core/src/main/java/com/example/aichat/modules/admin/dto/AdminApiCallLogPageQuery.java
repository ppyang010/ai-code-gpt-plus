package com.example.aichat.modules.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台模型调用日志分页查询入参。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminApiCallLogPageQuery extends AdminPageQuery {

    /** 按用户 ID 精确筛选。 */
    private Long userId;

    /** 按供应商 ID 精确筛选。 */
    private Long providerId;

    /** 按模型 ID 精确筛选。 */
    private Long modelId;

    /** 按调用是否成功筛选。 */
    private Integer successFlag;

    /** 起始时间，先按字符串契约固定，后续由查询逻辑统一解析。 */
    @Size(max = 32, message = "startTime length must be at most 32")
    private String startTime;

    /** 结束时间，先按字符串契约固定，后续由查询逻辑统一解析。 */
    @Size(max = 32, message = "endTime length must be at most 32")
    private String endTime;
}
