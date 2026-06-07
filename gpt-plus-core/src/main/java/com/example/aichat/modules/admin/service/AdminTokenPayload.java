package com.example.aichat.modules.admin.service;

/**
 * 后台 token 解析后的载荷。
 */
public class AdminTokenPayload {

    /** 当前 token 对应的后台管理员 ID。 */
    private Long adminUserId;
    /** token 发行方。 */
    private String issuer;
    /** token 签发时间戳。 */
    private Long issuedAtEpochSecond;
    /** token 过期时间戳。 */
    private Long expiresAtEpochSecond;
    /** token 唯一 ID。 */
    private String tokenId;

    public Long getAdminUserId() {
        return adminUserId;
    }

    public void setAdminUserId(Long adminUserId) {
        this.adminUserId = adminUserId;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Long getIssuedAtEpochSecond() {
        return issuedAtEpochSecond;
    }

    public void setIssuedAtEpochSecond(Long issuedAtEpochSecond) {
        this.issuedAtEpochSecond = issuedAtEpochSecond;
    }

    public Long getExpiresAtEpochSecond() {
        return expiresAtEpochSecond;
    }

    public void setExpiresAtEpochSecond(Long expiresAtEpochSecond) {
        this.expiresAtEpochSecond = expiresAtEpochSecond;
    }

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }
}
