package com.example.aichat.modules.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 后台鉴权配置。
 */
@Component
@ConfigurationProperties(prefix = "app.admin-auth")
public class AdminAuthProperties {

    /** HMAC token 签名密钥。 */
    private String tokenSecret = "local-admin-auth-secret-change-me";

    /** 后台 token 发行方标识。 */
    private String tokenIssuer = "gpt-plus-admin";

    /** 后台 token 过期秒数。 */
    private long tokenExpireSeconds = 604800L;

    public String getTokenSecret() {
        return tokenSecret;
    }

    public void setTokenSecret(String tokenSecret) {
        this.tokenSecret = tokenSecret;
    }

    public String getTokenIssuer() {
        return tokenIssuer;
    }

    public void setTokenIssuer(String tokenIssuer) {
        this.tokenIssuer = tokenIssuer;
    }

    public long getTokenExpireSeconds() {
        return tokenExpireSeconds;
    }

    public void setTokenExpireSeconds(long tokenExpireSeconds) {
        this.tokenExpireSeconds = tokenExpireSeconds;
    }
}
