package com.example.aichat.modules.admin.service.impl;

import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.modules.admin.config.AdminAuthProperties;
import com.example.aichat.modules.admin.service.AdminTokenPayload;
import com.example.aichat.modules.admin.service.AdminTokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * 基于 HMAC-SHA256 的后台 token 服务。
 *
 * <p>当前仓库没有额外 JWT 依赖，因此这里采用“JSON 载荷 + HMAC 签名”的轻量实现，
 * 优先满足后台登录、鉴权拦截和本地联调需要。</p>
 */
@Service
public class HmacAdminTokenService implements AdminTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int TOKEN_ID_BYTES = 12;

    private final AdminAuthProperties adminAuthProperties;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public HmacAdminTokenService(AdminAuthProperties adminAuthProperties, ObjectMapper objectMapper) {
        this.adminAuthProperties = adminAuthProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String createToken(Long adminUserId) {
        this.validateSecretConfigured();
        long issuedAt = Instant.now().getEpochSecond();
        AdminTokenPayload payload = new AdminTokenPayload();
        payload.setAdminUserId(adminUserId);
        payload.setIssuer(this.adminAuthProperties.getTokenIssuer());
        payload.setIssuedAtEpochSecond(issuedAt);
        payload.setExpiresAtEpochSecond(issuedAt + this.adminAuthProperties.getTokenExpireSeconds());
        payload.setTokenId(this.generateTokenId());
        String payloadSegment = this.encodePayload(payload);
        String signatureSegment = this.signPayloadSegment(payloadSegment);
        return payloadSegment + "." + signatureSegment;
    }

    @Override
    public AdminTokenPayload verifyToken(String token) {
        this.validateSecretConfigured();
        if (!StringUtils.hasText(token)) {
            throw new BizException(ErrorCode.ADMIN_AUTH_REQUIRED);
        }
        String[] segments = token.split("\\.");
        if (segments.length != 2) {
            throw new BizException(ErrorCode.ADMIN_AUTH_REQUIRED);
        }
        String expectedSignature = this.signPayloadSegment(segments[0]);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                segments[1].getBytes(StandardCharsets.UTF_8)
        )) {
            throw new BizException(ErrorCode.ADMIN_AUTH_REQUIRED);
        }
        AdminTokenPayload payload = this.decodePayload(segments[0]);
        if (!StringUtils.hasText(payload.getIssuer())
                || !payload.getIssuer().equals(this.adminAuthProperties.getTokenIssuer())
                || payload.getAdminUserId() == null
                || payload.getExpiresAtEpochSecond() == null
                || payload.getExpiresAtEpochSecond() <= Instant.now().getEpochSecond()) {
            throw new BizException(ErrorCode.ADMIN_AUTH_REQUIRED);
        }
        return payload;
    }

    /**
     * 将 token 载荷编码为 URL-safe Base64，兼容 HTTP Header 传输。
     */
    private String encodePayload(AdminTokenPayload payload) {
        try {
            byte[] payloadBytes = this.objectMapper.writeValueAsBytes(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encode admin token payload", exception);
        }
    }

    /**
     * 将 URL-safe Base64 载荷还原成 token payload。
     */
    private AdminTokenPayload decodePayload(String payloadSegment) {
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadSegment);
            return this.objectMapper.readValue(payloadBytes, AdminTokenPayload.class);
        } catch (Exception exception) {
            throw new BizException(ErrorCode.ADMIN_AUTH_REQUIRED);
        }
    }

    /**
     * 对 token 载荷做 HMAC-SHA256 签名，防止 token 被客户端篡改。
     */
    private String signPayloadSegment(String payloadSegment) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    this.adminAuthProperties.getTokenSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(keySpec);
            byte[] signature = mac.doFinal(payloadSegment.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign admin token", exception);
        }
    }

    /**
     * 生成 token 唯一 ID，避免同一秒登录拿到完全相同的 token。
     */
    private String generateTokenId() {
        byte[] tokenIdBytes = new byte[TOKEN_ID_BYTES];
        this.secureRandom.nextBytes(tokenIdBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenIdBytes);
    }

    /**
     * 登录和鉴权共用同一个密钥配置校验，缺失时直接阻止后台鉴权链路继续执行。
     */
    private void validateSecretConfigured() {
        if (!StringUtils.hasText(this.adminAuthProperties.getTokenSecret())) {
            throw new BizException(ErrorCode.ADMIN_AUTH_NOT_CONFIGURED);
        }
    }
}
