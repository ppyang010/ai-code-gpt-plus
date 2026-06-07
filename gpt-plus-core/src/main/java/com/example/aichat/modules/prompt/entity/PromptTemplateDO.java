package com.example.aichat.modules.prompt.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.model.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 提示词模板实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prompt_template")
public class PromptTemplateDO extends BaseDO {

    /** 模板编码。 */
    @TableField("template_code")
    private String templateCode;

    /** 模板名称。 */
    @TableField("template_name")
    private String templateName;

    /** 模板作用域。 */
    @TableField("template_scope")
    private String templateScope;

    /** 模板内容。 */
    @TableField("template_content")
    private String templateContent;

    /** 模板状态。 */
    @TableField("status")
    private Integer status;

    /** 排序值。 */
    @TableField("sort_no")
    private Integer sortNo;

    /** 备注。 */
    @TableField("remark")
    private String remark;
}
