package com.example.aichat.modules.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.model.entity.ModelConfigDO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfigDO> {

    ModelConfigDO selectByModelCode(@Param("modelCode") String modelCode);

    List<ModelConfigDO> selectEnabledModels();

    List<ModelConfigDO> selectByProviderId(@Param("providerId") Long providerId);

    ModelConfigDO selectEnabledById(@Param("id") Long id);
}
