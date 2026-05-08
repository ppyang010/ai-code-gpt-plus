package com.example.aichat.modules.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.billing.entity.UserTokenUsageDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserTokenUsageMapper extends BaseMapper<UserTokenUsageDO> {

    List<UserTokenUsageDO> selectDailyUsage(
            @Param("userId") Long userId,
            @Param("statDate") LocalDate statDate
    );

    Long sumTotalTokensByUserId(@Param("userId") Long userId);

    BigDecimal sumEstimatedCostByUserId(@Param("userId") Long userId);
}
