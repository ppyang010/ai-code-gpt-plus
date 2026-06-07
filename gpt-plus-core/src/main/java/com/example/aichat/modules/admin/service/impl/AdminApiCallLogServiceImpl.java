package com.example.aichat.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.modules.admin.dto.AdminApiCallLogPageQuery;
import com.example.aichat.modules.admin.service.AdminApiCallLogService;
import com.example.aichat.modules.admin.vo.AdminApiCallLogItemVO;
import com.example.aichat.modules.model.entity.ApiCallLogDO;
import com.example.aichat.modules.model.entity.ModelConfigDO;
import com.example.aichat.modules.model.entity.ModelProviderDO;
import com.example.aichat.modules.model.mapper.ApiCallLogMapper;
import com.example.aichat.modules.model.mapper.ModelConfigMapper;
import com.example.aichat.modules.model.mapper.ModelProviderMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 后台模型调用日志服务实现。
 */
@Service
public class AdminApiCallLogServiceImpl implements AdminApiCallLogService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 模型调用日志数据库访问器。 */
    private final ApiCallLogMapper apiCallLogMapper;
    /** 模型配置数据库访问器，用于补齐模型名称与编码。 */
    private final ModelConfigMapper modelConfigMapper;
    /** 模型供应商数据库访问器，用于补齐供应商名称。 */
    private final ModelProviderMapper modelProviderMapper;

    public AdminApiCallLogServiceImpl(
            ApiCallLogMapper apiCallLogMapper,
            ModelConfigMapper modelConfigMapper,
            ModelProviderMapper modelProviderMapper
    ) {
        this.apiCallLogMapper = apiCallLogMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.modelProviderMapper = modelProviderMapper;
    }

    @Override
    public PageResponse<AdminApiCallLogItemVO> pageLogs(AdminApiCallLogPageQuery query) {
        Page<ApiCallLogDO> apiCallLogPage = this.apiCallLogMapper.selectPage(
                new Page<>(this.normalizePageNo(query.getPageNo()), this.normalizePageSize(query.getPageSize())),
                this.buildPageWrapper(query)
        );
        Map<Long, ModelProviderDO> providerMap = this.loadProviderMap(apiCallLogPage.getRecords().stream()
                .map(ApiCallLogDO::getProviderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, ModelConfigDO> modelMap = this.loadModelMap(apiCallLogPage.getRecords().stream()
                .map(ApiCallLogDO::getModelId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        PageResponse<AdminApiCallLogItemVO> response = new PageResponse<>();
        response.setList(apiCallLogPage.getRecords().stream().map(apiCallLog -> this.toApiCallLogItem(apiCallLog, providerMap, modelMap)).toList());
        response.setTotal(apiCallLogPage.getTotal());
        response.setPageNo((int) apiCallLogPage.getCurrent());
        response.setPageSize((int) apiCallLogPage.getSize());
        return response;
    }

    @Override
    public AdminApiCallLogItemVO getLogDetail(Long logId) {
        ApiCallLogDO apiCallLog = this.requireApiCallLog(logId);
        ModelProviderDO provider = apiCallLog.getProviderId() == null ? null : this.modelProviderMapper.selectById(apiCallLog.getProviderId());
        ModelConfigDO model = apiCallLog.getModelId() == null ? null : this.modelConfigMapper.selectById(apiCallLog.getModelId());
        return this.toApiCallLogItem(
                apiCallLog,
                provider == null ? Map.of() : Map.of(provider.getId(), provider),
                model == null ? Map.of() : Map.of(model.getId(), model)
        );
    }

    /**
     * 构造调用日志分页查询条件，统一处理筛选条件、时间范围和默认排序。
     */
    private LambdaQueryWrapper<ApiCallLogDO> buildPageWrapper(AdminApiCallLogPageQuery query) {
        LambdaQueryWrapper<ApiCallLogDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getUserId() != null, ApiCallLogDO::getUserId, query.getUserId());
        wrapper.eq(query.getProviderId() != null, ApiCallLogDO::getProviderId, query.getProviderId());
        wrapper.eq(query.getModelId() != null, ApiCallLogDO::getModelId, query.getModelId());
        wrapper.eq(query.getSuccessFlag() != null, ApiCallLogDO::getSuccessFlag, query.getSuccessFlag());
        wrapper.ge(StringUtils.hasText(query.getStartTime()), ApiCallLogDO::getCreatedAt, this.parseStartDateTime(query.getStartTime()));
        wrapper.le(StringUtils.hasText(query.getEndTime()), ApiCallLogDO::getCreatedAt, this.parseEndDateTime(query.getEndTime()));
        wrapper.orderByDesc(ApiCallLogDO::getCreatedAt)
                .orderByDesc(ApiCallLogDO::getId);
        return wrapper;
    }

    /**
     * 查询单条调用日志；缺失时统一抛出调用日志不存在错误。
     */
    private ApiCallLogDO requireApiCallLog(Long logId) {
        ApiCallLogDO apiCallLog = this.apiCallLogMapper.selectById(logId);
        if (apiCallLog == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "模型调用日志不存在");
        }
        return apiCallLog;
    }

    /**
     * 批量加载模型供应商映射，避免调用日志列表页逐行查供应商。
     */
    private Map<Long, ModelProviderDO> loadProviderMap(Set<Long> providerIds) {
        if (providerIds.isEmpty()) {
            return Map.of();
        }
        return providerIds.stream()
                .map(this.modelProviderMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ModelProviderDO::getId, Function.identity(), (left, right) -> left));
    }

    /**
     * 批量加载模型配置映射，避免调用日志列表页逐行查模型。
     */
    private Map<Long, ModelConfigDO> loadModelMap(Set<Long> modelIds) {
        if (modelIds.isEmpty()) {
            return Map.of();
        }
        return modelIds.stream()
                .map(this.modelConfigMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ModelConfigDO::getId, Function.identity(), (left, right) -> left));
    }

    /**
     * 将调用日志实体、供应商和模型信息组装成统一后台响应对象。
     */
    private AdminApiCallLogItemVO toApiCallLogItem(
            ApiCallLogDO apiCallLog,
            Map<Long, ModelProviderDO> providerMap,
            Map<Long, ModelConfigDO> modelMap
    ) {
        ModelProviderDO provider = providerMap.get(apiCallLog.getProviderId());
        ModelConfigDO model = modelMap.get(apiCallLog.getModelId());
        AdminApiCallLogItemVO apiCallLogItem = new AdminApiCallLogItemVO();
        apiCallLogItem.setLogId(apiCallLog.getId());
        apiCallLogItem.setUserId(apiCallLog.getUserId());
        apiCallLogItem.setSessionId(apiCallLog.getSessionId());
        apiCallLogItem.setMessageId(apiCallLog.getMessageId());
        apiCallLogItem.setRequestId(apiCallLog.getRequestId());
        apiCallLogItem.setProviderId(apiCallLog.getProviderId());
        apiCallLogItem.setProviderName(provider == null ? null : provider.getProviderName());
        apiCallLogItem.setModelId(apiCallLog.getModelId());
        apiCallLogItem.setModelCode(model == null ? null : model.getModelCode());
        apiCallLogItem.setModelName(model == null ? null : model.getModelName());
        apiCallLogItem.setSuccessFlag(apiCallLog.getSuccessFlag());
        apiCallLogItem.setHttpStatus(apiCallLog.getHttpStatus());
        apiCallLogItem.setLatencyMs(apiCallLog.getLatencyMs());
        apiCallLogItem.setPromptTokens(apiCallLog.getPromptTokens());
        apiCallLogItem.setCompletionTokens(apiCallLog.getCompletionTokens());
        apiCallLogItem.setTotalTokens(apiCallLog.getTotalTokens());
        apiCallLogItem.setEstimatedCost(apiCallLog.getEstimatedCost());
        apiCallLogItem.setErrorCode(apiCallLog.getErrorCode());
        apiCallLogItem.setErrorMessage(apiCallLog.getErrorMessage());
        apiCallLogItem.setRequestPayload(apiCallLog.getRequestPayload());
        apiCallLogItem.setResponsePayload(apiCallLog.getResponsePayload());
        apiCallLogItem.setCreatedAt(apiCallLog.getCreatedAt());
        return apiCallLogItem;
    }

    /**
     * 起始时间支持 yyyy-MM-dd 和 yyyy-MM-dd HH:mm:ss 两种格式。
     */
    private LocalDateTime parseStartDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() == 10) {
            return LocalDate.parse(normalizedValue).atStartOfDay();
        }
        return LocalDateTime.parse(normalizedValue, DATE_TIME_FORMATTER);
    }

    /**
     * 结束时间支持 yyyy-MM-dd 和 yyyy-MM-dd HH:mm:ss 两种格式；仅给日期时自动补到当天 23:59:59。
     */
    private LocalDateTime parseEndDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() == 10) {
            return LocalDate.parse(normalizedValue).atTime(23, 59, 59);
        }
        return LocalDateTime.parse(normalizedValue, DATE_TIME_FORMATTER);
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
