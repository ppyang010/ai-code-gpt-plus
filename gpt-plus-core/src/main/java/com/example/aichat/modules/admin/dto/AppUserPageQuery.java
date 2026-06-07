package com.example.aichat.modules.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 普通用户后台分页查询入参。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AppUserPageQuery extends AdminPageQuery {

    /** 按用户名模糊筛选。 */
    @Size(max = 64, message = "username length must be at most 64")
    private String username;

    /** 按昵称模糊筛选。 */
    @Size(max = 64, message = "nickname length must be at most 64")
    private String nickname;

    /** 按用户状态筛选。 */
    private Integer status;
}
