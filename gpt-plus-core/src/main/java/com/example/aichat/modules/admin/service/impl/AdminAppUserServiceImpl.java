package com.example.aichat.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.modules.admin.dto.AppUserPageQuery;
import com.example.aichat.modules.admin.dto.AppUserStatusUpdateRequest;
import com.example.aichat.modules.admin.service.AdminAppUserService;
import com.example.aichat.modules.admin.vo.AppUserAdminItemVO;
import com.example.aichat.modules.billing.entity.UserBalanceDO;
import com.example.aichat.modules.billing.entity.UserTokenUsageDO;
import com.example.aichat.modules.billing.mapper.UserBalanceMapper;
import com.example.aichat.modules.billing.mapper.UserTokenUsageMapper;
import com.example.aichat.modules.model.entity.ApiCallLogDO;
import com.example.aichat.modules.model.mapper.ApiCallLogMapper;
import com.example.aichat.modules.user.entity.AppUserDO;
import com.example.aichat.modules.user.mapper.AppUserMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 普通用户后台管理服务实现。
 */
@Service
public class AdminAppUserServiceImpl implements AdminAppUserService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    /** 普通用户数据库访问器。 */
    private final AppUserMapper appUserMapper;
    /** 用户余额数据库访问器。 */
    private final UserBalanceMapper userBalanceMapper;
    /** 用户 token 用量数据库访问器。 */
    private final UserTokenUsageMapper userTokenUsageMapper;
    /** 模型调用日志数据库访问器，用于汇总最近请求时间。 */
    private final ApiCallLogMapper apiCallLogMapper;

    public AdminAppUserServiceImpl(
            AppUserMapper appUserMapper,
            UserBalanceMapper userBalanceMapper,
            UserTokenUsageMapper userTokenUsageMapper,
            ApiCallLogMapper apiCallLogMapper
    ) {
        this.appUserMapper = appUserMapper;
        this.userBalanceMapper = userBalanceMapper;
        this.userTokenUsageMapper = userTokenUsageMapper;
        this.apiCallLogMapper = apiCallLogMapper;
    }

    @Override
    public PageResponse<AppUserAdminItemVO> pageAppUsers(AppUserPageQuery query) {
        Page<AppUserDO> appUserPage = this.appUserMapper.selectPage(
                new Page<>(this.normalizePageNo(query.getPageNo()), this.normalizePageSize(query.getPageSize())),
                this.buildPageWrapper(query)
        );
        List<AppUserDO> users = appUserPage.getRecords();
        Map<Long, UserBalanceDO> balanceMap = this.loadBalanceMap(users.stream().map(AppUserDO::getId).collect(Collectors.toSet()));
        Map<Long, LocalDateTime> lastRequestTimeMap = this.loadLastRequestTimeMap(users.stream().map(AppUserDO::getId).collect(Collectors.toSet()));
        PageResponse<AppUserAdminItemVO> response = new PageResponse<>();
        response.setList(users.stream().map(user -> this.toAppUserItem(user, balanceMap.get(user.getId()), null, lastRequestTimeMap.get(user.getId()))).toList());
        response.setTotal(appUserPage.getTotal());
        response.setPageNo((int) appUserPage.getCurrent());
        response.setPageSize((int) appUserPage.getSize());
        return response;
    }

    @Override
    public AppUserAdminItemVO getAppUserDetail(Long userId) {
        AppUserDO appUser = this.requireAppUser(userId);
        UserBalanceDO userBalance = this.userBalanceMapper.selectByUserId(userId);
        AppUserUsageSummary usageSummary = this.buildUsageSummary(this.userTokenUsageMapper.selectList(
                new LambdaQueryWrapper<UserTokenUsageDO>()
                        .eq(UserTokenUsageDO::getUserId, userId)
        ));
        LocalDateTime lastRequestAt = this.loadLastRequestTimeMap(Set.of(userId)).get(userId);
        return this.toAppUserItem(appUser, userBalance, usageSummary, lastRequestAt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAppUserStatus(AppUserStatusUpdateRequest request) {
        AppUserDO appUser = this.requireAppUser(request.getUserId());
        appUser.setStatus(this.normalizeStatus(request.getStatus(), appUser.getStatus()));
        this.appUserMapper.updateById(appUser);
    }

    /**
     * 构造普通用户分页查询条件，统一处理模糊搜索、状态过滤和默认排序。
     */
    private LambdaQueryWrapper<AppUserDO> buildPageWrapper(AppUserPageQuery query) {
        LambdaQueryWrapper<AppUserDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getUsername()), AppUserDO::getUsername, query.getUsername().trim());
        wrapper.like(StringUtils.hasText(query.getNickname()), AppUserDO::getNickname, query.getNickname().trim());
        wrapper.eq(query.getStatus() != null, AppUserDO::getStatus, query.getStatus());
        wrapper.orderByDesc(AppUserDO::getUpdatedAt)
                .orderByDesc(AppUserDO::getId);
        return wrapper;
    }

    /**
     * 查询单个普通用户；缺失时统一抛出用户不存在错误。
     */
    private AppUserDO requireAppUser(Long userId) {
        AppUserDO appUser = this.appUserMapper.selectById(userId);
        if (appUser == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "普通用户不存在");
        }
        return appUser;
    }

    /**
     * 批量加载用户余额映射，避免普通用户列表页逐行查余额。
     */
    private Map<Long, UserBalanceDO> loadBalanceMap(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return this.userBalanceMapper.selectList(
                        new LambdaQueryWrapper<UserBalanceDO>().in(UserBalanceDO::getUserId, userIds)
                ).stream()
                .collect(Collectors.toMap(UserBalanceDO::getUserId, balance -> balance, (left, right) -> left));
    }

    /**
     * 批量加载最近请求时间，优先从调用日志中取最新一条 created_at。
     */
    private Map<Long, LocalDateTime> loadLastRequestTimeMap(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, LocalDateTime> lastRequestTimeMap = new HashMap<>();
        List<ApiCallLogDO> apiCallLogs = this.apiCallLogMapper.selectList(
                new LambdaQueryWrapper<ApiCallLogDO>()
                        .in(ApiCallLogDO::getUserId, userIds)
                        .orderByDesc(ApiCallLogDO::getCreatedAt)
        );
        for (ApiCallLogDO apiCallLog : apiCallLogs) {
            lastRequestTimeMap.putIfAbsent(apiCallLog.getUserId(), apiCallLog.getCreatedAt());
        }
        return lastRequestTimeMap;
    }

    /**
     * 将用户 token 用量明细聚合成普通用户详情页所需摘要。
     */
    private AppUserUsageSummary buildUsageSummary(List<UserTokenUsageDO> usages) {
        AppUserUsageSummary usageSummary = new AppUserUsageSummary();
        if (usages == null || usages.isEmpty()) {
            usageSummary.setTotalTokens(0L);
            usageSummary.setEstimatedCost(BigDecimal.ZERO);
            return usageSummary;
        }
        long totalTokens = 0L;
        BigDecimal estimatedCost = BigDecimal.ZERO;
        for (UserTokenUsageDO usage : usages) {
            totalTokens += usage.getTotalTokens() == null ? 0 : usage.getTotalTokens();
            estimatedCost = estimatedCost.add(usage.getEstimatedCost() == null ? BigDecimal.ZERO : usage.getEstimatedCost());
        }
        usageSummary.setTotalTokens(totalTokens);
        usageSummary.setEstimatedCost(estimatedCost);
        return usageSummary;
    }

    /**
     * 将普通用户实体、余额与用量摘要组装成统一后台响应对象。
     */
    private AppUserAdminItemVO toAppUserItem(
            AppUserDO appUser,
            UserBalanceDO userBalance,
            AppUserUsageSummary usageSummary,
            LocalDateTime lastRequestAt
    ) {
        AppUserAdminItemVO appUserItem = new AppUserAdminItemVO();
        appUserItem.setUserId(appUser.getId());
        appUserItem.setUsername(appUser.getUsername());
        appUserItem.setNickname(appUser.getNickname());
        appUserItem.setEmail(appUser.getEmail());
        appUserItem.setMobile(appUser.getMobile());
        appUserItem.setStatus(appUser.getStatus());
        appUserItem.setBalanceAmount(userBalance == null ? BigDecimal.ZERO : userBalance.getBalanceAmount());
        appUserItem.setLastLoginAt(appUser.getLastLoginAt());
        appUserItem.setLastRequestAt(lastRequestAt);
        appUserItem.setTotalTokens(usageSummary == null ? 0L : usageSummary.getTotalTokens());
        appUserItem.setEstimatedCost(usageSummary == null ? BigDecimal.ZERO : usageSummary.getEstimatedCost());
        appUserItem.setCreatedAt(appUser.getCreatedAt());
        appUserItem.setUpdatedAt(appUser.getUpdatedAt());
        return appUserItem;
    }

    /**
     * 将状态字段标准化为 0/1，非法值一律拒绝。
     */
    private Integer normalizeStatus(Integer status, Integer defaultValue) {
        if (status == null) {
            return defaultValue;
        }
        if (!Objects.equals(status, STATUS_ENABLED) && !Objects.equals(status, STATUS_DISABLED)) {
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

    /**
     * 普通用户详情页用量摘要的轻量聚合对象，避免把统计中间态暴露到公共 VO。
     */
    private static class AppUserUsageSummary {

        /** 当前用户累计总 token。 */
        private Long totalTokens;
        /** 当前用户累计预估成本。 */
        private BigDecimal estimatedCost;

        public Long getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(Long totalTokens) {
            this.totalTokens = totalTokens;
        }

        public BigDecimal getEstimatedCost() {
            return estimatedCost;
        }

        public void setEstimatedCost(BigDecimal estimatedCost) {
            this.estimatedCost = estimatedCost;
        }
    }
}
