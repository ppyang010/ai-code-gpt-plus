package com.example.aichat.modules.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.model.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model_provider")
public class ModelProviderDO extends BaseDO {

    @TableField("provider_code")
    private String providerCode;

    @TableField("provider_name")
    private String providerName;

    @TableField("base_url")
    private String baseUrl;

    @TableField("api_key_encrypted")
    private String apiKeyEncrypted;

    @TableField("default_headers")
    private String defaultHeaders;

    @TableField("status")
    private Integer status;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField("remark")
    private String remark;
}
