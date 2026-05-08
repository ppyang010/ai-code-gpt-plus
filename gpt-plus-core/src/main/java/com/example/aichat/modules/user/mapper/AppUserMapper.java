package com.example.aichat.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.user.entity.AppUserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUserDO> {

    AppUserDO selectByUsername(@Param("username") String username);

    AppUserDO selectByEmail(@Param("email") String email);

    AppUserDO selectByMobile(@Param("mobile") String mobile);
}
