package com.example.aichat.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.modules.admin.dto.AdminUserPageQuery;
import com.example.aichat.modules.admin.dto.AdminUserResetPasswordRequest;
import com.example.aichat.modules.admin.dto.AdminUserSaveRequest;
import com.example.aichat.modules.admin.dto.AdminUserStatusUpdateRequest;
import com.example.aichat.modules.admin.entity.AdminUserDO;
import com.example.aichat.modules.admin.mapper.AdminUserMapper;
import com.example.aichat.modules.admin.service.AdminPasswordService;
import com.example.aichat.modules.admin.service.AdminUserService;
import com.example.aichat.modules.admin.vo.AdminUserItemVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 后台管理员管理服务实现。
 */
@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    /** 后台管理员数据库访问器。 */
    private final AdminUserMapper adminUserMapper;
    /** 后台管理员密码哈希服务。 */
    private final AdminPasswordService adminPasswordService;

    public AdminUserServiceImpl(AdminUserMapper adminUserMapper, AdminPasswordService adminPasswordService) {
        this.adminUserMapper = adminUserMapper;
        this.adminPasswordService = adminPasswordService;
    }

    @Override
    public PageResponse<AdminUserItemVO> pageAdminUsers(AdminUserPageQuery query) {
        Page<AdminUserDO> adminUserPage = this.adminUserMapper.selectPage(
                new Page<>(this.normalizePageNo(query.getPageNo()), this.normalizePageSize(query.getPageSize())),
                this.buildPageWrapper(query)
        );
        PageResponse<AdminUserItemVO> response = new PageResponse<>();
        response.setList(adminUserPage.getRecords().stream().map(this::toAdminUserItem).toList());
        response.setTotal(adminUserPage.getTotal());
        response.setPageNo((int) adminUserPage.getCurrent());
        response.setPageSize((int) adminUserPage.getSize());
        return response;
    }

    @Override
    public AdminUserItemVO getAdminUserDetail(Long adminUserId) {
        return this.toAdminUserItem(this.requireAdminUser(adminUserId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createAdminUser(AdminUserSaveRequest request) {
        String normalizedUsername = this.normalizeRequiredText(request.getUsername(), "管理员用户名不能为空");
        if (this.adminUserMapper.selectByUsername(normalizedUsername) != null) {
            throw new BizException(ErrorCode.ADMIN_LOGIN_INVALID, "管理员用户名已存在");
        }
        String normalizedPassword = this.normalizeRequiredText(request.getPassword(), "管理员初始密码不能为空");
        AdminUserDO adminUser = new AdminUserDO();
        adminUser.setUsername(normalizedUsername);
        adminUser.setPasswordHash(this.adminPasswordService.encode(normalizedPassword));
        adminUser.setNickname(this.normalizeOptionalText(request.getNickname()));
        adminUser.setEmail(this.normalizeOptionalText(request.getEmail()));
        adminUser.setMobile(this.normalizeOptionalText(request.getMobile()));
        adminUser.setStatus(this.normalizeStatus(request.getStatus(), STATUS_ENABLED));
        this.adminUserMapper.insert(adminUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAdminUser(AdminUserSaveRequest request) {
        if (request.getAdminUserId() == null) {
            throw new BizException(ErrorCode.ADMIN_AUTH_REQUIRED, "管理员不存在");
        }
        AdminUserDO adminUser = this.requireAdminUser(request.getAdminUserId());
        String normalizedUsername = this.normalizeRequiredText(request.getUsername(), "管理员用户名不能为空");
        AdminUserDO duplicateAdminUser = this.adminUserMapper.selectByUsername(normalizedUsername);
        if (duplicateAdminUser != null && !duplicateAdminUser.getId().equals(adminUser.getId())) {
            throw new BizException(ErrorCode.ADMIN_LOGIN_INVALID, "管理员用户名已存在");
        }
        adminUser.setUsername(normalizedUsername);
        adminUser.setNickname(this.normalizeOptionalText(request.getNickname()));
        adminUser.setEmail(this.normalizeOptionalText(request.getEmail()));
        adminUser.setMobile(this.normalizeOptionalText(request.getMobile()));
        adminUser.setStatus(this.normalizeStatus(request.getStatus(), adminUser.getStatus()));
        this.adminUserMapper.updateById(adminUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAdminUserStatus(AdminUserStatusUpdateRequest request) {
        AdminUserDO adminUser = this.requireAdminUser(request.getAdminUserId());
        adminUser.setStatus(this.normalizeStatus(request.getStatus(), adminUser.getStatus()));
        this.adminUserMapper.updateById(adminUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetAdminUserPassword(AdminUserResetPasswordRequest request) {
        AdminUserDO adminUser = this.requireAdminUser(request.getAdminUserId());
        String normalizedPassword = this.normalizeRequiredText(request.getNewPassword(), "重置密码不能为空");
        adminUser.setPasswordHash(this.adminPasswordService.encode(normalizedPassword));
        this.adminUserMapper.updateById(adminUser);
    }

    /**
     * 构造后台管理员分页查询条件，统一处理模糊搜索、状态过滤和默认排序。
     */
    private LambdaQueryWrapper<AdminUserDO> buildPageWrapper(AdminUserPageQuery query) {
        LambdaQueryWrapper<AdminUserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getUsername()), AdminUserDO::getUsername, query.getUsername().trim());
        wrapper.like(StringUtils.hasText(query.getNickname()), AdminUserDO::getNickname, query.getNickname().trim());
        wrapper.eq(query.getStatus() != null, AdminUserDO::getStatus, query.getStatus());
        wrapper.orderByDesc(AdminUserDO::getUpdatedAt)
                .orderByDesc(AdminUserDO::getId);
        return wrapper;
    }

    /**
     * 查询单个后台管理员；缺失时统一抛出管理员不存在错误。
     */
    private AdminUserDO requireAdminUser(Long adminUserId) {
        AdminUserDO adminUser = this.adminUserMapper.selectById(adminUserId);
        if (adminUser == null) {
            throw new BizException(ErrorCode.ADMIN_AUTH_REQUIRED, "管理员不存在");
        }
        return adminUser;
    }

    /**
     * 将后台管理员实体转换为列表或详情统一响应对象。
     */
    private AdminUserItemVO toAdminUserItem(AdminUserDO adminUser) {
        AdminUserItemVO adminUserItem = new AdminUserItemVO();
        adminUserItem.setAdminUserId(adminUser.getId());
        adminUserItem.setUsername(adminUser.getUsername());
        adminUserItem.setNickname(adminUser.getNickname());
        adminUserItem.setEmail(adminUser.getEmail());
        adminUserItem.setMobile(adminUser.getMobile());
        adminUserItem.setStatus(adminUser.getStatus());
        adminUserItem.setLastLoginAt(adminUser.getLastLoginAt());
        adminUserItem.setCreatedAt(adminUser.getCreatedAt());
        adminUserItem.setUpdatedAt(adminUser.getUpdatedAt());
        return adminUserItem;
    }

    /**
     * 将必填文本统一做 trim，并在缺失时返回面向前端的中文错误。
     */
    private String normalizeRequiredText(String value, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.INVALID_PARAM, errorMessage);
        }
        return value.trim();
    }

    /**
     * 将可选文本统一做 trim 和空字符串归一化。
     */
    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 将状态字段标准化为 0/1，非法值一律拒绝。
     */
    private Integer normalizeStatus(Integer status, Integer defaultValue) {
        if (status == null) {
            return defaultValue;
        }
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new BizException(ErrorCode.INVALID_PARAM, "状态值只能是 0 或 1");
        }
        return status;
    }

    /**
     * 后台分页页码统一从 1 开始，未传或非法值时回退到第一页。
     */
    private int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    /**
     * 后台分页大小统一限制到 100 以内，和全局分页插件约束保持一致。
     */
    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
