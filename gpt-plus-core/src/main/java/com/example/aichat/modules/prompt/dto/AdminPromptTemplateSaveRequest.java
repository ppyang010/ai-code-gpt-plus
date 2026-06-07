package com.example.aichat.modules.prompt.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台提示词模板新增或编辑请求。
 */
@Data
public class AdminPromptTemplateSaveRequest {

    /** 模板 ID；创建时可为空，更新时由后续逻辑校验必填。 */
    private Long templateId;

    /** 模板编码。 */
    @Size(max = 64, message = "templateCode length must be at most 64")
    private String templateCode;

    /** 模板名称。 */
    @Size(max = 128, message = "templateName length must be at most 128")
    private String templateName;

    /** 模板作用域。 */
    @Size(max = 32, message = "templateScope length must be at most 32")
    private String templateScope;

    /** 模板内容。 */
    @Size(max = 4000, message = "templateContent length must be at most 4000")
    private String templateContent;

    /** 模板状态。 */
    private Integer status;

    /** 排序值。 */
    private Integer sortNo;

    /** 备注。 */
    @Size(max = 255, message = "remark length must be at most 255")
    private String remark;
}
