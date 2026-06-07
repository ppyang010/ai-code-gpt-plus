package com.example.aichat.modules.model.dto;

import com.example.aichat.modules.admin.dto.AdminPageQuery;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台模型配置分页查询入参。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminModelConfigPageQuery extends AdminPageQuery {

    /** 按供应商 ID 精确筛选。 */
    private Long providerId;

    /** 按模型编码模糊筛选。 */
    @Size(max = 128, message = "modelCode length must be at most 128")
    private String modelCode;

    /** 按模型名称模糊筛选。 */
    @Size(max = 128, message = "modelName length must be at most 128")
    private String modelName;

    /** 按模型状态筛选。 */
    private Integer status;
}
