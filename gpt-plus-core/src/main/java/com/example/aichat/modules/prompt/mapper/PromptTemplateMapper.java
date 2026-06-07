package com.example.aichat.modules.prompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.prompt.entity.PromptTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 提示词模板 Mapper。
 */
@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplateDO> {

    /**
     * 按模板编码查询提示词模板。
     */
    PromptTemplateDO selectByTemplateCode(@Param("templateCode") String templateCode);
}
