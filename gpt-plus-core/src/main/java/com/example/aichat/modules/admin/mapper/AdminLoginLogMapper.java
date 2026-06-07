package com.example.aichat.modules.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.admin.entity.AdminLoginLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 后台管理员登录日志 Mapper。
 */
@Mapper
public interface AdminLoginLogMapper extends BaseMapper<AdminLoginLogDO> {
}
