package com.example.aichat.modules.model.dto;

import com.example.aichat.modules.admin.dto.AdminPageQuery;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台模型供应商分页查询入参。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminModelProviderPageQuery extends AdminPageQuery {

    /** 按供应商编码模糊筛选。 */
    @Size(max = 64, message = "providerCode length must be at most 64")
    private String providerCode;

    /** 按供应商名称模糊筛选。 */
    @Size(max = 128, message = "providerName length must be at most 128")
    private String providerName;

    /** 按供应商状态筛选。 */
    private Integer status;
}
