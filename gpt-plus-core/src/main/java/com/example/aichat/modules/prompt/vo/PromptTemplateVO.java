package com.example.aichat.modules.prompt.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 后台提示词模板响应对象。
 */
@Data
public class PromptTemplateVO {

    /** 模板 ID。 */
    private Long templateId;

    /** 模板编码。 */
    private String templateCode;

    /** 模板名称。 */
    private String templateName;

    /** 模板作用域。 */
    private String templateScope;

    /** 模板内容。 */
    private String templateContent;

    /** 模板状态。 */
    private Integer status;

    /** 排序值。 */
    private Integer sortNo;

    /** 备注。 */
    private String remark;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
