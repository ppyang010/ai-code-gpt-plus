package com.example.aichat.modules.model.controller;

import com.example.aichat.common.dto.CommonResponse;
import com.example.aichat.modules.model.service.ModelConfigService;
import com.example.aichat.modules.model.vo.ModelOptionVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/model")
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    public ModelConfigController(ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    @GetMapping("/list")
    public CommonResponse<List<ModelOptionVO>> listEnabledModels() {
        return CommonResponse.success(modelConfigService.listEnabledModels());
    }
}
