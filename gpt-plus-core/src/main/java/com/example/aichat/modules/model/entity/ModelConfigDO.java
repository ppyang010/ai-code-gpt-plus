package com.example.aichat.modules.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.model.BaseDO;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model_config")
public class ModelConfigDO extends BaseDO {

    @TableField("provider_id")
    private Long providerId;

    @TableField("model_code")
    private String modelCode;

    @TableField("model_name")
    private String modelName;

    @TableField("model_type")
    private String modelType;

    @TableField("support_stream")
    private Integer supportStream;

    @TableField("support_thinking")
    private Integer supportThinking;

    @TableField("support_json_output")
    private Integer supportJsonOutput;

    @TableField("support_vision")
    private Integer supportVision;

    @TableField("support_file")
    private Integer supportFile;

    @TableField("context_window")
    private Integer contextWindow;

    @TableField("max_output_tokens")
    private Integer maxOutputTokens;

    @TableField("temperature_default")
    private BigDecimal temperatureDefault;

    @TableField("top_p_default")
    private BigDecimal topPDefault;

    @TableField("extra_config")
    private String extraConfig;

    @TableField("status")
    private Integer status;

    @TableField("sort_no")
    private Integer sortNo;
}
