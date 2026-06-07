package com.example.aichat.modules.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.admin.entity.AdminUserDO;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 后台管理员 Mapper。
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUserDO> {

    /**
     * 根据用户名查询后台管理员。
     */
    AdminUserDO selectByUsername(@Param("username") String username);

    /**
     * 查询状态正常的后台管理员。
     */
    AdminUserDO selectActiveById(@Param("adminUserId") Long adminUserId);

    /**
     * 回写最近一次登录时间。
     */
    int updateLastLoginAt(
            @Param("adminUserId") Long adminUserId,
            @Param("lastLoginAt") LocalDateTime lastLoginAt
    );
}
