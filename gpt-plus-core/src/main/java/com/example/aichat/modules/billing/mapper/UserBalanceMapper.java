package com.example.aichat.modules.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.billing.entity.UserBalanceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserBalanceMapper extends BaseMapper<UserBalanceDO> {

    UserBalanceDO selectByUserId(@Param("userId") Long userId);
}
