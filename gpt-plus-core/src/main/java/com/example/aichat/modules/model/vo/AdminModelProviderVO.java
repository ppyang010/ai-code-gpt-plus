package com.example.aichat.modules.model.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 后台模型供应商响应对象。
 */
@Data
public class AdminModelProviderVO {

    /** 供应商 ID。 */
    private Long providerId;

    /** 供应商编码。 */
    private String providerCode;

    /** 供应商名称。 */
    private String providerName;

    /** 供应商基础地址。 */
    private String baseUrl;

    /** 是否已配置 API Key。 */
    private Boolean apiKeyConfigured;

    /** API Key 脱敏预览。 */
    private String apiKeyMaskedPreview;

    /** 默认请求头 JSON 字符串。 */
    private String defaultHeaders;

    /** 供应商状态。 */
    private Integer status;

    /** 排序值。 */
    private Integer sortNo;

    /** 备注。 */
    private String remark;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
