package com.example.aichat.modules.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 后台分页查询公共入参。
 */
@Data
public class AdminPageQuery {

    /** 页码，未传时后续由服务层兜底默认值。 */
    @Positive(message = "pageNo must be greater than 0")
    private Integer pageNo;

    /** 每页条数，统一限制在 1 到 100 之间。 */
    @Min(value = 1, message = "pageSize must be greater than 0")
    @Max(value = 100, message = "pageSize must be at most 100")
    private Integer pageSize;
}
