package com.example.aichat.modules.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.modules.prompt.dto.AdminPromptTemplatePageQuery;
import com.example.aichat.modules.prompt.dto.AdminPromptTemplateSaveRequest;
import com.example.aichat.modules.prompt.dto.AdminPromptTemplateStatusUpdateRequest;
import com.example.aichat.modules.prompt.entity.PromptTemplateDO;
import com.example.aichat.modules.prompt.mapper.PromptTemplateMapper;
import com.example.aichat.modules.prompt.service.PromptTemplateService;
import com.example.aichat.modules.prompt.vo.PromptTemplateVO;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 提示词模板服务实现。
 */
@Service
public class PromptTemplateServiceImpl implements PromptTemplateService {

    /** 首版参与聊天主链路拼装的全局附加提示词作用域。 */
    public static final String SCOPE_GLOBAL_APPEND = "global_append";
    /** 首版仅做配置存储、不参与主链路拼装的场景附加提示词作用域。 */
    public static final String SCOPE_SCENE_APPEND = "scene_append";
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    /** 提示词模板数据库访问器。 */
    private final PromptTemplateMapper promptTemplateMapper;

    public PromptTemplateServiceImpl(PromptTemplateMapper promptTemplateMapper) {
        this.promptTemplateMapper = promptTemplateMapper;
    }

    @Override
    public PageResponse<PromptTemplateVO> pageTemplates(AdminPromptTemplatePageQuery query) {
        Page<PromptTemplateDO> templatePage = this.promptTemplateMapper.selectPage(
                new Page<>(this.normalizePageNo(query.getPageNo()), this.normalizePageSize(query.getPageSize())),
                this.buildPageWrapper(query)
        );
        PageResponse<PromptTemplateVO> response = new PageResponse<>();
        response.setList(templatePage.getRecords().stream().map(this::toTemplateVO).toList());
        response.setTotal(templatePage.getTotal());
        response.setPageNo((int) templatePage.getCurrent());
        response.setPageSize((int) templatePage.getSize());
        return response;
    }

    @Override
    public PromptTemplateVO getTemplateDetail(Long templateId) {
        return this.toTemplateVO(this.requireTemplate(templateId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createTemplate(AdminPromptTemplateSaveRequest request) {
        String normalizedTemplateCode = this.normalizeRequiredText(request.getTemplateCode(), "提示词模板编码不能为空");
        if (this.promptTemplateMapper.selectByTemplateCode(normalizedTemplateCode) != null) {
            throw new BizException(ErrorCode.ADMIN_PROMPT_TEMPLATE_CODE_DUPLICATE);
        }
        PromptTemplateDO template = new PromptTemplateDO();
        template.setTemplateCode(normalizedTemplateCode);
        template.setTemplateName(this.normalizeRequiredText(request.getTemplateName(), "提示词模板名称不能为空"));
        template.setTemplateScope(this.normalizeTemplateScope(request.getTemplateScope()));
        template.setTemplateContent(this.normalizeTemplateContent(request.getTemplateContent()));
        template.setStatus(this.normalizeStatus(request.getStatus(), STATUS_DISABLED));
        template.setSortNo(this.normalizeSortNo(request.getSortNo()));
        template.setRemark(this.normalizeOptionalText(request.getRemark()));
        this.promptTemplateMapper.insert(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTemplate(AdminPromptTemplateSaveRequest request) {
        if (request.getTemplateId() == null) {
            throw new BizException(ErrorCode.ADMIN_PROMPT_TEMPLATE_NOT_FOUND);
        }
        PromptTemplateDO existingTemplate = this.requireTemplate(request.getTemplateId());
        String normalizedTemplateCode = this.normalizeRequiredText(request.getTemplateCode(), "提示词模板编码不能为空");
        PromptTemplateDO duplicateTemplate = this.promptTemplateMapper.selectByTemplateCode(normalizedTemplateCode);
        if (duplicateTemplate != null && !duplicateTemplate.getId().equals(existingTemplate.getId())) {
            throw new BizException(ErrorCode.ADMIN_PROMPT_TEMPLATE_CODE_DUPLICATE);
        }
        existingTemplate.setTemplateCode(normalizedTemplateCode);
        existingTemplate.setTemplateName(this.normalizeRequiredText(request.getTemplateName(), "提示词模板名称不能为空"));
        existingTemplate.setTemplateScope(this.normalizeTemplateScope(request.getTemplateScope()));
        existingTemplate.setTemplateContent(this.normalizeTemplateContent(request.getTemplateContent()));
        existingTemplate.setStatus(this.normalizeStatus(request.getStatus(), existingTemplate.getStatus()));
        existingTemplate.setSortNo(this.normalizeSortNo(request.getSortNo(), existingTemplate.getSortNo()));
        existingTemplate.setRemark(this.normalizeOptionalText(request.getRemark()));
        this.promptTemplateMapper.updateById(existingTemplate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTemplateStatus(AdminPromptTemplateStatusUpdateRequest request) {
        PromptTemplateDO template = this.requireTemplate(request.getTemplateId());
        template.setStatus(this.normalizeStatus(request.getStatus(), template.getStatus()));
        this.promptTemplateMapper.updateById(template);
    }

    @Override
    public List<String> listEnabledGlobalPromptContents() {
        return this.promptTemplateMapper.selectList(
                        new LambdaQueryWrapper<PromptTemplateDO>()
                                .eq(PromptTemplateDO::getTemplateScope, SCOPE_GLOBAL_APPEND)
                                .eq(PromptTemplateDO::getStatus, STATUS_ENABLED)
                                .orderByAsc(PromptTemplateDO::getSortNo)
                                .orderByAsc(PromptTemplateDO::getId)
                ).stream()
                .map(PromptTemplateDO::getTemplateContent)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    /**
     * 构造提示词模板分页查询条件，统一处理模糊搜索、状态过滤和默认排序。
     */
    private LambdaQueryWrapper<PromptTemplateDO> buildPageWrapper(AdminPromptTemplatePageQuery query) {
        LambdaQueryWrapper<PromptTemplateDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getTemplateCode()), PromptTemplateDO::getTemplateCode, query.getTemplateCode().trim());
        wrapper.like(StringUtils.hasText(query.getTemplateName()), PromptTemplateDO::getTemplateName, query.getTemplateName().trim());
        wrapper.eq(StringUtils.hasText(query.getTemplateScope()), PromptTemplateDO::getTemplateScope, query.getTemplateScope().trim());
        wrapper.eq(query.getStatus() != null, PromptTemplateDO::getStatus, query.getStatus());
        wrapper.orderByAsc(PromptTemplateDO::getSortNo)
                .orderByAsc(PromptTemplateDO::getId);
        return wrapper;
    }

    /**
     * 查询单个提示词模板；缺失时统一抛出模板不存在错误。
     */
    private PromptTemplateDO requireTemplate(Long templateId) {
        PromptTemplateDO template = this.promptTemplateMapper.selectById(templateId);
        if (template == null) {
            throw new BizException(ErrorCode.ADMIN_PROMPT_TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    /**
     * 将提示词模板实体转换为后台视图对象。
     */
    private PromptTemplateVO toTemplateVO(PromptTemplateDO template) {
        PromptTemplateVO templateVO = new PromptTemplateVO();
        templateVO.setTemplateId(template.getId());
        templateVO.setTemplateCode(template.getTemplateCode());
        templateVO.setTemplateName(template.getTemplateName());
        templateVO.setTemplateScope(template.getTemplateScope());
        templateVO.setTemplateContent(template.getTemplateContent());
        templateVO.setStatus(template.getStatus());
        templateVO.setSortNo(template.getSortNo());
        templateVO.setRemark(template.getRemark());
        templateVO.setUpdatedAt(template.getUpdatedAt());
        return templateVO;
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
     * 提示词模板内容允许多行，但不允许只有空白字符。
     */
    private String normalizeTemplateContent(String templateContent) {
        if (!StringUtils.hasText(templateContent)) {
            throw new BizException(ErrorCode.ADMIN_PROMPT_TEMPLATE_EMPTY);
        }
        return templateContent.trim();
    }

    /**
     * 首版只允许写入当前系统支持的两个模板作用域，避免提前写入无效 scope。
     */
    private String normalizeTemplateScope(String templateScope) {
        String normalizedTemplateScope = this.normalizeRequiredText(templateScope, "提示词模板作用域不能为空");
        if (!SCOPE_GLOBAL_APPEND.equals(normalizedTemplateScope) && !SCOPE_SCENE_APPEND.equals(normalizedTemplateScope)) {
            throw new BizException(ErrorCode.ADMIN_PROMPT_TEMPLATE_SCOPE_INVALID);
        }
        return normalizedTemplateScope;
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
     * 将可选文本统一做 trim 和空字符串归一化。
     */
    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
