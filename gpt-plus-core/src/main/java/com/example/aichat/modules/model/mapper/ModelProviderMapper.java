package com.example.aichat.modules.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.model.entity.ModelProviderDO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ModelProviderMapper extends BaseMapper<ModelProviderDO> {

    ModelProviderDO selectByProviderCode(@Param("providerCode") String providerCode);

    List<ModelProviderDO> selectEnabledProviders();
}
