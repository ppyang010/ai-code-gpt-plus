package com.example.aichat.modules.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.model.entity.ApiCallLogDO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ApiCallLogMapper extends BaseMapper<ApiCallLogDO> {

    ApiCallLogDO selectByRequestId(@Param("requestId") String requestId);

    List<ApiCallLogDO> selectRecentFailures(@Param("limit") Integer limit);
}
