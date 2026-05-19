package com.example.aichat.modules.model.service;

import com.example.aichat.modules.model.vo.ModelOptionVO;
import java.util.List;

public interface ModelConfigService {

    List<ModelOptionVO> listEnabledModels();
}
