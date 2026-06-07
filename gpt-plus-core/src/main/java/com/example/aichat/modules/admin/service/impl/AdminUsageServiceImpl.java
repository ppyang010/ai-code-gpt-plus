package com.example.aichat.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aichat.modules.admin.dto.AdminUsageDailyQuery;
import com.example.aichat.modules.admin.service.AdminUsageService;
import com.example.aichat.modules.admin.vo.AdminUsageDailyItemVO;
import com.example.aichat.modules.admin.vo.AdminUserUsageSummaryVO;
import com.example.aichat.modules.billing.entity.UserTokenUsageDO;
import com.example.aichat.modules.billing.mapper.UserTokenUsageMapper;
import com.example.aichat.modules.model.entity.ApiCallLogDO;
import com.example.aichat.modules.model.mapper.ApiCallLogMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 后台用量统计服务实现。
 */
@Service
public class AdminUsageServiceImpl implements AdminUsageService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 用户 token 用量数据库访问器。 */
    private final UserTokenUsageMapper userTokenUsageMapper;
    /** 模型调用日志数据库访问器，用于补最近请求时间。 */
    private final ApiCallLogMapper apiCallLogMapper;

    public AdminUsageServiceImpl(
            UserTokenUsageMapper userTokenUsageMapper,
            ApiCallLogMapper apiCallLogMapper
    ) {
        this.userTokenUsageMapper = userTokenUsageMapper;
        this.apiCallLogMapper = apiCallLogMapper;
    }

    @Override
    public List<AdminUsageDailyItemVO> listDailyUsage(AdminUsageDailyQuery query) {
        List<UserTokenUsageDO> usages = this.userTokenUsageMapper.selectList(this.buildUsageWrapper(query));
        Map<LocalDate, UsageAccumulator> usageAccumulatorMap = new LinkedHashMap<>();
        for (UserTokenUsageDO usage : usages) {
            LocalDate statDate = usage.getStatDate();
            UsageAccumulator usageAccumulator = usageAccumulatorMap.computeIfAbsent(statDate, unused -> new UsageAccumulator());
            usageAccumulator.addUsage(usage);
        }
        List<AdminUsageDailyItemVO> dailyUsageItems = new ArrayList<>();
        usageAccumulatorMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> dailyUsageItems.add(this.toDailyItem(entry.getKey(), entry.getValue())));
        return dailyUsageItems;
    }

    @Override
    public AdminUserUsageSummaryVO getUserUsageSummary(Long userId) {
        List<UserTokenUsageDO> usages = this.userTokenUsageMapper.selectList(
                new LambdaQueryWrapper<UserTokenUsageDO>().eq(UserTokenUsageDO::getUserId, userId)
        );
        UsageAccumulator usageAccumulator = new UsageAccumulator();
        for (UserTokenUsageDO usage : usages) {
            usageAccumulator.addUsage(usage);
        }
        AdminUserUsageSummaryVO usageSummary = new AdminUserUsageSummaryVO();
        usageSummary.setUserId(userId);
        usageSummary.setTotalPromptTokens((int) usageAccumulator.getPromptTokens());
        usageSummary.setTotalCompletionTokens((int) usageAccumulator.getCompletionTokens());
        usageSummary.setTotalTokens((int) usageAccumulator.getTotalTokens());
        usageSummary.setEstimatedCost(usageAccumulator.getEstimatedCost());
        usageSummary.setLastRequestAt(this.loadLastRequestAt(userId));
        return usageSummary;
    }

    /**
     * 构造用量查询条件，统一处理日期范围和用户/供应商/模型过滤。
     */
    private LambdaQueryWrapper<UserTokenUsageDO> buildUsageWrapper(AdminUsageDailyQuery query) {
        LambdaQueryWrapper<UserTokenUsageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(StringUtils.hasText(query.getStartDate()), UserTokenUsageDO::getStatDate, this.parseDate(query.getStartDate()));
        wrapper.le(StringUtils.hasText(query.getEndDate()), UserTokenUsageDO::getStatDate, this.parseDate(query.getEndDate()));
        wrapper.eq(query.getUserId() != null, UserTokenUsageDO::getUserId, query.getUserId());
        wrapper.eq(query.getProviderId() != null, UserTokenUsageDO::getProviderId, query.getProviderId());
        wrapper.eq(query.getModelId() != null, UserTokenUsageDO::getModelId, query.getModelId());
        wrapper.orderByAsc(UserTokenUsageDO::getStatDate)
                .orderByAsc(UserTokenUsageDO::getId);
        return wrapper;
    }

    /**
     * 将聚合中间态转换为每日用量响应对象。
     */
    private AdminUsageDailyItemVO toDailyItem(LocalDate statDate, UsageAccumulator usageAccumulator) {
        AdminUsageDailyItemVO dailyItem = new AdminUsageDailyItemVO();
        dailyItem.setStatDate(statDate);
        dailyItem.setUserCount(usageAccumulator.getUserIds().size());
        dailyItem.setRequestCount(usageAccumulator.getRequestCount());
        dailyItem.setPromptTokens((int) usageAccumulator.getPromptTokens());
        dailyItem.setCompletionTokens((int) usageAccumulator.getCompletionTokens());
        dailyItem.setTotalTokens((int) usageAccumulator.getTotalTokens());
        dailyItem.setEstimatedCost(usageAccumulator.getEstimatedCost());
        return dailyItem;
    }

    /**
     * 查询单个用户最近一次请求时间，用于后台详情页辅助判断活跃度。
     */
    private LocalDateTime loadLastRequestAt(Long userId) {
        ApiCallLogDO latestApiCallLog = this.apiCallLogMapper.selectOne(
                new LambdaQueryWrapper<ApiCallLogDO>()
                        .eq(ApiCallLogDO::getUserId, userId)
                        .orderByDesc(ApiCallLogDO::getCreatedAt)
                        .last("LIMIT 1")
        );
        return latestApiCallLog == null ? null : latestApiCallLog.getCreatedAt();
    }

    /**
     * 日期查询统一使用 yyyy-MM-dd，避免后台统计接口出现多种日期字符串歧义。
     */
    private LocalDate parseDate(String value) {
        return LocalDate.parse(value.trim(), DATE_FORMATTER);
    }

    /**
     * 每日用量聚合中间态，同时统计人数、请求数、token 和预估成本。
     */
    private static class UsageAccumulator {

        /** 当日涉及的用户集合，用于统计 userCount。 */
        private final java.util.Set<Long> userIds = new java.util.HashSet<>();
        /** 当日请求数。 */
        private int requestCount;
        /** 当日输入 token 总数。 */
        private long promptTokens;
        /** 当日输出 token 总数。 */
        private long completionTokens;
        /** 当日总 token 数。 */
        private long totalTokens;
        /** 当日累计预估成本。 */
        private BigDecimal estimatedCost = BigDecimal.ZERO;

        /**
         * 将一条 token 用量明细累加到当前统计窗口。
         */
        private void addUsage(UserTokenUsageDO usage) {
            if (usage.getUserId() != null) {
                this.userIds.add(usage.getUserId());
            }
            this.requestCount++;
            this.promptTokens += usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
            this.completionTokens += usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
            this.totalTokens += usage.getTotalTokens() == null ? 0 : usage.getTotalTokens();
            this.estimatedCost = this.estimatedCost.add(usage.getEstimatedCost() == null ? BigDecimal.ZERO : usage.getEstimatedCost());
        }

        public java.util.Set<Long> getUserIds() {
            return userIds;
        }

        public int getRequestCount() {
            return requestCount;
        }

        public long getPromptTokens() {
            return promptTokens;
        }

        public long getCompletionTokens() {
            return completionTokens;
        }

        public long getTotalTokens() {
            return totalTokens;
        }

        public BigDecimal getEstimatedCost() {
            return estimatedCost;
        }
    }
}
