package com.example.aichat.modules.model.service.impl;

import com.example.aichat.modules.model.entity.ModelConfigDO;
import com.example.aichat.modules.model.mapper.ModelConfigMapper;
import com.example.aichat.modules.model.service.ModelConfigService;
import com.example.aichat.modules.model.vo.ModelOptionVO;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ModelConfigServiceImpl implements ModelConfigService {

    private final ModelConfigMapper modelConfigMapper;

    public ModelConfigServiceImpl(ModelConfigMapper modelConfigMapper) {
        this.modelConfigMapper = modelConfigMapper;
    }

    @Override
    public List<ModelOptionVO> listEnabledModels() {
        return modelConfigMapper.selectEnabledModels().stream()
                .map(this::toOption)
                .toList();
    }

    private ModelOptionVO toOption(ModelConfigDO model) {
        // 前端只需要展示和能力开关字段，不暴露 provider/api_key 等供应商内部配置。
        ModelOptionVO option = new ModelOptionVO();
        option.setId(model.getId());
        option.setCode(model.getModelCode());
        option.setLabel(model.getModelName());
        option.setModelType(model.getModelType());
        option.setSupportStream(toBoolean(model.getSupportStream()));
        option.setSupportThinking(toBoolean(model.getSupportThinking()));
        option.setSupportJsonOutput(toBoolean(model.getSupportJsonOutput()));
        option.setSupportVision(toBoolean(model.getSupportVision()));
        option.setSupportFile(toBoolean(model.getSupportFile()));
        option.setContextWindow(model.getContextWindow());
        option.setMaxOutputTokens(model.getMaxOutputTokens());
        return option;
    }

    private Boolean toBoolean(Integer value) {
        return value != null && value == 1;
    }
}
