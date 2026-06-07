package com.example.aichat.modules.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.modules.model.dto.AdminModelConfigPageQuery;
import com.example.aichat.modules.model.dto.AdminModelConfigSaveRequest;
import com.example.aichat.modules.model.dto.AdminModelConfigStatusUpdateRequest;
import com.example.aichat.modules.model.entity.ModelConfigDO;
import com.example.aichat.modules.model.entity.ModelProviderDO;
import com.example.aichat.modules.model.mapper.ModelConfigMapper;
import com.example.aichat.modules.model.mapper.ModelProviderMapper;
import com.example.aichat.modules.model.service.ModelConfigAdminService;
import com.example.aichat.modules.model.vo.AdminModelConfigVO;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 后台模型配置管理服务实现。
 */
@Service
public class ModelConfigAdminServiceImpl implements ModelConfigAdminService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    /** 模型配置数据库访问器。 */
    private final ModelConfigMapper modelConfigMapper;
    /** 模型供应商数据库访问器，用于校验 provider 存在性并组装展示字段。 */
    private final ModelProviderMapper modelProviderMapper;
    /** JSON 解析器，用于校验模型扩展配置格式。 */
    private final ObjectMapper objectMapper;

    public ModelConfigAdminServiceImpl(
            ModelConfigMapper modelConfigMapper,
            ModelProviderMapper modelProviderMapper,
            ObjectMapper objectMapper
    ) {
        this.modelConfigMapper = modelConfigMapper;
        this.modelProviderMapper = modelProviderMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResponse<AdminModelConfigVO> pageConfigs(AdminModelConfigPageQuery query) {
        Page<ModelConfigDO> configPage = this.modelConfigMapper.selectPage(
                new Page<>(this.normalizePageNo(query.getPageNo()), this.normalizePageSize(query.getPageSize())),
                this.buildPageWrapper(query)
        );
        Map<Long, ModelProviderDO> providerMap = this.loadProviderMap(configPage.getRecords().stream()
                .map(ModelConfigDO::getProviderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        PageResponse<AdminModelConfigVO> response = new PageResponse<>();
        response.setList(configPage.getRecords().stream().map(config -> this.toConfigVO(config, providerMap)).toList());
        response.setTotal(configPage.getTotal());
        response.setPageNo((int) configPage.getCurrent());
        response.setPageSize((int) configPage.getSize());
        return response;
    }

    @Override
    public AdminModelConfigVO getConfigDetail(Long modelId) {
        ModelConfigDO config = this.requireConfig(modelId);
        ModelProviderDO provider = this.requireProvider(config.getProviderId());
        return this.toConfigVO(config, Map.of(provider.getId(), provider));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createConfig(AdminModelConfigSaveRequest request) {
        String normalizedModelCode = this.normalizeRequiredText(request.getModelCode(), "模型编码不能为空");
        if (this.modelConfigMapper.selectByModelCode(normalizedModelCode) != null) {
            throw new BizException(ErrorCode.ADMIN_MODEL_CONFIG_CODE_DUPLICATE);
        }
        ModelProviderDO provider = this.requireProvider(request.getProviderId());
        ModelConfigDO config = new ModelConfigDO();
        config.setProviderId(provider.getId());
        config.setModelCode(normalizedModelCode);
        config.setModelName(this.normalizeRequiredText(request.getModelName(), "模型名称不能为空"));
        config.setModelType(this.normalizeModelType(request.getModelType()));
        config.setSupportStream(this.normalizeFlag(request.getSupportStream(), 1));
        config.setSupportThinking(this.normalizeFlag(request.getSupportThinking(), 0));
        config.setSupportJsonOutput(this.normalizeFlag(request.getSupportJsonOutput(), 0));
        config.setSupportVision(this.normalizeFlag(request.getSupportVision(), 0));
        config.setSupportFile(this.normalizeFlag(request.getSupportFile(), 0));
        config.setContextWindow(request.getContextWindow());
        config.setMaxOutputTokens(request.getMaxOutputTokens());
        config.setTemperatureDefault(request.getTemperatureDefault());
        config.setTopPDefault(request.getTopPDefault());
        config.setExtraConfig(this.normalizeJsonIfPresent(request.getExtraConfig(), "模型扩展配置格式不正确"));
        config.setStatus(this.normalizeFlag(request.getStatus(), STATUS_ENABLED));
        config.setSortNo(this.normalizeSortNo(request.getSortNo()));
        this.modelConfigMapper.insert(config);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(AdminModelConfigSaveRequest request) {
        if (request.getModelId() == null) {
            throw new BizException(ErrorCode.ADMIN_MODEL_CONFIG_NOT_FOUND);
        }
        ModelConfigDO existingConfig = this.requireConfig(request.getModelId());
        String normalizedModelCode = this.normalizeRequiredText(request.getModelCode(), "模型编码不能为空");
        ModelConfigDO duplicateConfig = this.modelConfigMapper.selectByModelCode(normalizedModelCode);
        if (duplicateConfig != null && !duplicateConfig.getId().equals(existingConfig.getId())) {
            throw new BizException(ErrorCode.ADMIN_MODEL_CONFIG_CODE_DUPLICATE);
        }
        ModelProviderDO provider = this.requireProvider(request.getProviderId());
        existingConfig.setProviderId(provider.getId());
        existingConfig.setModelCode(normalizedModelCode);
        existingConfig.setModelName(this.normalizeRequiredText(request.getModelName(), "模型名称不能为空"));
        existingConfig.setModelType(this.normalizeModelType(request.getModelType()));
        existingConfig.setSupportStream(this.normalizeFlag(request.getSupportStream(), existingConfig.getSupportStream()));
        existingConfig.setSupportThinking(this.normalizeFlag(request.getSupportThinking(), existingConfig.getSupportThinking()));
        existingConfig.setSupportJsonOutput(this.normalizeFlag(request.getSupportJsonOutput(), existingConfig.getSupportJsonOutput()));
        existingConfig.setSupportVision(this.normalizeFlag(request.getSupportVision(), existingConfig.getSupportVision()));
        existingConfig.setSupportFile(this.normalizeFlag(request.getSupportFile(), existingConfig.getSupportFile()));
        existingConfig.setContextWindow(request.getContextWindow());
        existingConfig.setMaxOutputTokens(request.getMaxOutputTokens());
        existingConfig.setTemperatureDefault(request.getTemperatureDefault());
        existingConfig.setTopPDefault(request.getTopPDefault());
        existingConfig.setExtraConfig(this.normalizeJsonIfPresent(request.getExtraConfig(), "模型扩展配置格式不正确"));
        existingConfig.setStatus(this.normalizeFlag(request.getStatus(), existingConfig.getStatus()));
        existingConfig.setSortNo(this.normalizeSortNo(request.getSortNo(), existingConfig.getSortNo()));
        this.modelConfigMapper.updateById(existingConfig);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfigStatus(AdminModelConfigStatusUpdateRequest request) {
        ModelConfigDO config = this.requireConfig(request.getModelId());
        config.setStatus(this.normalizeFlag(request.getStatus(), config.getStatus()));
        this.modelConfigMapper.updateById(config);
    }

    /**
     * 构造模型配置分页查询条件，统一处理模糊搜索、状态过滤和默认排序。
     */
    private LambdaQueryWrapper<ModelConfigDO> buildPageWrapper(AdminModelConfigPageQuery query) {
        LambdaQueryWrapper<ModelConfigDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getProviderId() != null, ModelConfigDO::getProviderId, query.getProviderId());
        wrapper.like(StringUtils.hasText(query.getModelCode()), ModelConfigDO::getModelCode, query.getModelCode().trim());
        wrapper.like(StringUtils.hasText(query.getModelName()), ModelConfigDO::getModelName, query.getModelName().trim());
        wrapper.eq(query.getStatus() != null, ModelConfigDO::getStatus, query.getStatus());
        wrapper.orderByAsc(ModelConfigDO::getSortNo)
                .orderByAsc(ModelConfigDO::getId);
        return wrapper;
    }

    /**
     * 查询单个模型配置；缺失时统一抛出模型配置不存在错误。
     */
    private ModelConfigDO requireConfig(Long modelId) {
        ModelConfigDO config = this.modelConfigMapper.selectById(modelId);
        if (config == null) {
            throw new BizException(ErrorCode.ADMIN_MODEL_CONFIG_NOT_FOUND);
        }
        return config;
    }

    /**
     * 查询单个模型供应商；缺失时统一抛出模型供应商不存在错误。
     */
    private ModelProviderDO requireProvider(Long providerId) {
        if (providerId == null) {
            throw new BizException(ErrorCode.ADMIN_MODEL_PROVIDER_NOT_FOUND);
        }
        ModelProviderDO provider = this.modelProviderMapper.selectById(providerId);
        if (provider == null) {
            throw new BizException(ErrorCode.ADMIN_MODEL_PROVIDER_NOT_FOUND);
        }
        return provider;
    }

    /**
     * 批量加载供应商映射，避免模型配置列表页对每一行单独查一次供应商。
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
     * 将模型配置实体转换为后台视图对象，并补齐供应商编码和名称。
     */
    private AdminModelConfigVO toConfigVO(ModelConfigDO config, Map<Long, ModelProviderDO> providerMap) {
        ModelProviderDO provider = providerMap.get(config.getProviderId());
        AdminModelConfigVO configVO = new AdminModelConfigVO();
        configVO.setModelId(config.getId());
        configVO.setProviderId(config.getProviderId());
        configVO.setProviderCode(provider == null ? null : provider.getProviderCode());
        configVO.setProviderName(provider == null ? null : provider.getProviderName());
        configVO.setModelCode(config.getModelCode());
        configVO.setModelName(config.getModelName());
        configVO.setModelType(config.getModelType());
        configVO.setSupportStream(config.getSupportStream());
        configVO.setSupportThinking(config.getSupportThinking());
        configVO.setSupportJsonOutput(config.getSupportJsonOutput());
        configVO.setSupportVision(config.getSupportVision());
        configVO.setSupportFile(config.getSupportFile());
        configVO.setContextWindow(config.getContextWindow());
        configVO.setMaxOutputTokens(config.getMaxOutputTokens());
        configVO.setTemperatureDefault(config.getTemperatureDefault());
        configVO.setTopPDefault(config.getTopPDefault());
        configVO.setExtraConfig(config.getExtraConfig());
        configVO.setStatus(config.getStatus());
        configVO.setSortNo(config.getSortNo());
        configVO.setUpdatedAt(config.getUpdatedAt());
        return configVO;
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
     * 统一规范模型类型；当前后台只做聊天模型链路，未传时默认回落到 chat。
     */
    private String normalizeModelType(String modelType) {
        return StringUtils.hasText(modelType) ? modelType.trim() : "chat";
    }

    /**
     * 将布尔型状态字段标准化为 0/1，非法值一律拒绝。
     */
    private Integer normalizeFlag(Integer flag, Integer defaultValue) {
        if (flag == null) {
            return defaultValue;
        }
        if (flag != STATUS_ENABLED && flag != STATUS_DISABLED) {
            throw new BizException(ErrorCode.INVALID_PARAM, "开关状态值只能是 0 或 1");
        }
        return flag;
    }

    /**
     * 验证扩展配置 JSON 字符串是否合法；当前后端只存储原文，后续真正使用时再细分结构。
     */
    private String normalizeJsonIfPresent(String value, String errorMessage) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalizedValue = value.trim();
        try {
            JsonNode jsonNode = this.objectMapper.readTree(normalizedValue);
            if (jsonNode == null || !jsonNode.isObject()) {
                throw new BizException(ErrorCode.INVALID_PARAM, errorMessage);
            }
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(ErrorCode.INVALID_PARAM, errorMessage);
        }
        return normalizedValue;
    }

    /**
     * 新建时缺省排序值使用 0，和现有模型数据风格保持一致。
     */
    private Integer normalizeSortNo(Integer sortNo) {
        return this.normalizeSortNo(sortNo, 0);
    }

    /**
     * 未显式传排序值时保留原值或兜底值。
     */
    private Integer normalizeSortNo(Integer sortNo, Integer defaultValue) {
        return sortNo == null ? defaultValue : sortNo;
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
