package com.example.aichat.modules.prompt.dto;

import com.example.aichat.modules.admin.dto.AdminPageQuery;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 后台提示词模板分页查询入参。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminPromptTemplatePageQuery extends AdminPageQuery {

    /** 按模板编码模糊筛选。 */
    @Size(max = 64, message = "templateCode length must be at most 64")
    private String templateCode;

    /** 按模板名称模糊筛选。 */
    @Size(max = 128, message = "templateName length must be at most 128")
    private String templateName;

    /** 按模板作用域筛选。 */
    @Size(max = 32, message = "templateScope length must be at most 32")
    private String templateScope;

    /** 按模板状态筛选。 */
    private Integer status;
}
