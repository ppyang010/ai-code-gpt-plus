package com.example.aichat.modules.billing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.model.BaseDO;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_balance")
public class UserBalanceDO extends BaseDO {

    @TableField("user_id")
    private Long userId;

    @TableField("balance_amount")
    private BigDecimal balanceAmount;

    @TableField("total_recharged")
    private BigDecimal totalRecharged;

    @TableField("total_consumed")
    private BigDecimal totalConsumed;

    @TableField("currency")
    private String currency;

    @TableField("status")
    private Integer status;
}
