package com.example.aichat.modules.model.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.modules.admin.service.AdminSecretCryptoService;
import com.example.aichat.modules.model.dto.AdminModelProviderPageQuery;
import com.example.aichat.modules.model.dto.AdminModelProviderSaveRequest;
import com.example.aichat.modules.model.dto.AdminModelProviderStatusUpdateRequest;
import com.example.aichat.modules.model.entity.ModelProviderDO;
import com.example.aichat.modules.model.mapper.ModelProviderMapper;
import com.example.aichat.modules.model.service.ModelProviderAdminService;
import com.example.aichat.modules.model.vo.AdminModelProviderVO;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 后台模型供应商管理服务实现。
 */
@Service
public class ModelProviderAdminServiceImpl implements ModelProviderAdminService {

    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    /** 模型供应商数据库访问器。 */
    private final ModelProviderMapper modelProviderMapper;
    /** 后台敏感字段加解密服务，仅用于 API Key 落库与脱敏预览。 */
    private final AdminSecretCryptoService adminSecretCryptoService;
    /** JSON 解析器，用于校验默认请求头配置格式。 */
    private final ObjectMapper objectMapper;

    public ModelProviderAdminServiceImpl(
            ModelProviderMapper modelProviderMapper,
            AdminSecretCryptoService adminSecretCryptoService,
            ObjectMapper objectMapper
    ) {
        this.modelProviderMapper = modelProviderMapper;
        this.adminSecretCryptoService = adminSecretCryptoService;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResponse<AdminModelProviderVO> pageProviders(AdminModelProviderPageQuery query) {
        Page<ModelProviderDO> providerPage = this.modelProviderMapper.selectPage(
                new Page<>(this.normalizePageNo(query.getPageNo()), this.normalizePageSize(query.getPageSize())),
                this.buildPageWrapper(query)
        );
        PageResponse<AdminModelProviderVO> response = new PageResponse<>();
        response.setList(providerPage.getRecords().stream().map(this::toProviderVO).toList());
        response.setTotal(providerPage.getTotal());
        response.setPageNo((int) providerPage.getCurrent());
        response.setPageSize((int) providerPage.getSize());
        return response;
    }

    @Override
    public AdminModelProviderVO getProviderDetail(Long providerId) {
        return this.toProviderVO(this.requireProvider(providerId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createProvider(AdminModelProviderSaveRequest request) {
        String normalizedProviderCode = this.normalizeRequiredText(request.getProviderCode(), "模型供应商编码不能为空");
        if (this.modelProviderMapper.selectByProviderCode(normalizedProviderCode) != null) {
            throw new BizException(ErrorCode.ADMIN_MODEL_PROVIDER_CODE_DUPLICATE);
        }
        ModelProviderDO provider = new ModelProviderDO();
        provider.setProviderCode(normalizedProviderCode);
        provider.setProviderName(this.normalizeRequiredText(request.getProviderName(), "模型供应商名称不能为空"));
        provider.setBaseUrl(this.normalizeOptionalText(request.getBaseUrl()));
        provider.setApiKeyEncrypted(this.encryptApiKeyIfPresent(request.getApiKey()));
        provider.setDefaultHeaders(this.normalizeJsonIfPresent(request.getDefaultHeaders(), "默认请求头格式不正确"));
        provider.setStatus(this.normalizeStatus(request.getStatus(), STATUS_ENABLED));
        provider.setSortNo(this.normalizeSortNo(request.getSortNo()));
        provider.setRemark(this.normalizeOptionalText(request.getRemark()));
        this.modelProviderMapper.insert(provider);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProvider(AdminModelProviderSaveRequest request) {
        if (request.getProviderId() == null) {
            throw new BizException(ErrorCode.ADMIN_MODEL_PROVIDER_NOT_FOUND);
        }
        ModelProviderDO existingProvider = this.requireProvider(request.getProviderId());
        String normalizedProviderCode = this.normalizeRequiredText(request.getProviderCode(), "模型供应商编码不能为空");
        ModelProviderDO duplicateProvider = this.modelProviderMapper.selectByProviderCode(normalizedProviderCode);
        if (duplicateProvider != null && !duplicateProvider.getId().equals(existingProvider.getId())) {
            throw new BizException(ErrorCode.ADMIN_MODEL_PROVIDER_CODE_DUPLICATE);
        }
        existingProvider.setProviderCode(normalizedProviderCode);
        existingProvider.setProviderName(this.normalizeRequiredText(request.getProviderName(), "模型供应商名称不能为空"));
        existingProvider.setBaseUrl(this.normalizeOptionalText(request.getBaseUrl()));
        existingProvider.setDefaultHeaders(this.normalizeJsonIfPresent(request.getDefaultHeaders(), "默认请求头格式不正确"));
        existingProvider.setStatus(this.normalizeStatus(request.getStatus(), existingProvider.getStatus()));
        existingProvider.setSortNo(this.normalizeSortNo(request.getSortNo(), existingProvider.getSortNo()));
        existingProvider.setRemark(this.normalizeOptionalText(request.getRemark()));
        if (StringUtils.hasText(request.getApiKey())) {
            existingProvider.setApiKeyEncrypted(this.encryptApiKeyIfPresent(request.getApiKey()));
        }
        this.modelProviderMapper.updateById(existingProvider);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProviderStatus(AdminModelProviderStatusUpdateRequest request) {
        ModelProviderDO provider = this.requireProvider(request.getProviderId());
        provider.setStatus(this.normalizeStatus(request.getStatus(), provider.getStatus()));
        this.modelProviderMapper.updateById(provider);
    }

    /**
     * 构造模型供应商分页查询条件，统一处理模糊搜索、状态过滤和默认排序。
     */
    private LambdaQueryWrapper<ModelProviderDO> buildPageWrapper(AdminModelProviderPageQuery query) {
        LambdaQueryWrapper<ModelProviderDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getProviderCode()), ModelProviderDO::getProviderCode, query.getProviderCode().trim());
        wrapper.like(StringUtils.hasText(query.getProviderName()), ModelProviderDO::getProviderName, query.getProviderName().trim());
        wrapper.eq(query.getStatus() != null, ModelProviderDO::getStatus, query.getStatus());
        wrapper.orderByAsc(ModelProviderDO::getSortNo)
                .orderByAsc(ModelProviderDO::getId);
        return wrapper;
    }

    /**
     * 查询单个模型供应商；缺失时统一抛出供应商不存在错误。
     */
    private ModelProviderDO requireProvider(Long providerId) {
        ModelProviderDO provider = this.modelProviderMapper.selectById(providerId);
        if (provider == null) {
            throw new BizException(ErrorCode.ADMIN_MODEL_PROVIDER_NOT_FOUND);
        }
        return provider;
    }

    /**
     * 将数据库实体转换为后台供应商视图对象，并在存在密钥时生成脱敏预览。
     */
    private AdminModelProviderVO toProviderVO(ModelProviderDO provider) {
        AdminModelProviderVO providerVO = new AdminModelProviderVO();
        providerVO.setProviderId(provider.getId());
        providerVO.setProviderCode(provider.getProviderCode());
        providerVO.setProviderName(provider.getProviderName());
        providerVO.setBaseUrl(provider.getBaseUrl());
        providerVO.setApiKeyConfigured(StringUtils.hasText(provider.getApiKeyEncrypted()));
        providerVO.setApiKeyMaskedPreview(this.maskApiKeyPreview(provider.getApiKeyEncrypted()));
        providerVO.setDefaultHeaders(provider.getDefaultHeaders());
        providerVO.setStatus(provider.getStatus());
        providerVO.setSortNo(provider.getSortNo());
        providerVO.setRemark(provider.getRemark());
        providerVO.setUpdatedAt(provider.getUpdatedAt());
        return providerVO;
    }

    /**
     * 仅在传入明文 API Key 时做加密落库，未传时保留原值。
     */
    private String encryptApiKeyIfPresent(String apiKey) {
        return StringUtils.hasText(apiKey) ? this.adminSecretCryptoService.encrypt(apiKey.trim()) : null;
    }

    /**
     * 供应商详情只返回脱敏预览，不直接暴露完整密钥。
     */
    private String maskApiKeyPreview(String encryptedApiKey) {
        if (!StringUtils.hasText(encryptedApiKey)) {
            return null;
        }
        try {
            String plainApiKey = this.adminSecretCryptoService.decrypt(encryptedApiKey);
            if (!StringUtils.hasText(plainApiKey)) {
                return null;
            }
            String trimmedApiKey = plainApiKey.trim();
            if (trimmedApiKey.length() <= 8) {
                return trimmedApiKey.substring(0, Math.min(2, trimmedApiKey.length()))
                        + "****"
                        + trimmedApiKey.substring(Math.max(trimmedApiKey.length() - 2, 0));
            }
            return trimmedApiKey.substring(0, 4) + "****" + trimmedApiKey.substring(trimmedApiKey.length() - 4);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 将可选文本统一做 trim 和空字符串归一化，避免把空白字符原样写入数据库。
     */
    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
     * 验证 JSON 字符串是否合法；当前后端只存储原文，后续真正使用时再细分结构。
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
     * 将状态字段标准化为 0/1，非法值一律拒绝，避免用户误传其他数字。
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
