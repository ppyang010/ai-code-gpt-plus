package com.example.aichat.modules.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 后台模型供应商新增或编辑请求。
 */
@Data
public class AdminModelProviderSaveRequest {

    /** 供应商 ID；创建时可为空，更新时由后续逻辑校验必填。 */
    private Long providerId;

    /** 供应商编码。 */
    @Size(max = 64, message = "providerCode length must be at most 64")
    private String providerCode;

    /** 供应商名称。 */
    @Size(max = 128, message = "providerName length must be at most 128")
    private String providerName;

    /** 供应商基础地址。 */
    @Size(max = 255, message = "baseUrl length must be at most 255")
    private String baseUrl;

    /** 明文 API Key 入参，后续由服务层完成加密落库。 */
    @Size(max = 1024, message = "apiKey length must be at most 1024")
    private String apiKey;

    /** 默认请求头 JSON 字符串。 */
    @Size(max = 4000, message = "defaultHeaders length must be at most 4000")
    private String defaultHeaders;

    /** 供应商状态。 */
    private Integer status;

    /** 排序值。 */
    private Integer sortNo;

    /** 备注。 */
    @Size(max = 255, message = "remark length must be at most 255")
    private String remark;
}
