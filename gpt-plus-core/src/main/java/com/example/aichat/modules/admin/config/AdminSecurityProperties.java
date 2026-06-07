package com.example.aichat.modules.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 后台敏感信息安全配置。
 */
@Component
@ConfigurationProperties(prefix = "app.admin-security")
public class AdminSecurityProperties {

    /** 对模型供应商 API Key 做对称加密的本地密钥。 */
    private String encryptionSecret = "local-admin-security-secret-change-me";

    public String getEncryptionSecret() {
        return encryptionSecret;
    }

    public void setEncryptionSecret(String encryptionSecret) {
        this.encryptionSecret = encryptionSecret;
    }
}
