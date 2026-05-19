package com.example.aichat.modules.model.vo;

import lombok.Data;

@Data
public class ModelOptionVO {

    private Long id;
    private String code;
    private String label;
    private String modelType;
    private Boolean supportStream;
    private Boolean supportThinking;
    private Boolean supportJsonOutput;
    private Boolean supportVision;
    private Boolean supportFile;
    private Integer contextWindow;
    private Integer maxOutputTokens;
}
