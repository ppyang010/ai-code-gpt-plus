package com.example.aichat.modules.admin.service.impl;

import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.modules.admin.config.AdminSecurityProperties;
import com.example.aichat.modules.admin.service.AdminSecretCryptoService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 基于 AES-GCM 的后台敏感字段加解密服务。
 */
@Service
public class AesAdminSecretCryptoService implements AdminSecretCryptoService {

    private static final String AES_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final AdminSecurityProperties adminSecurityProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesAdminSecretCryptoService(AdminSecurityProperties adminSecurityProperties) {
        this.adminSecurityProperties = adminSecurityProperties;
    }

    @Override
    public String encrypt(String plainText) {
        this.validateSecretConfigured();
        if (!StringUtils.hasText(plainText)) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            this.secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, this.buildSecretKeySpec(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] mergedBytes = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, mergedBytes, 0, iv.length);
            System.arraycopy(cipherBytes, 0, mergedBytes, iv.length, cipherBytes.length);
            return Base64.getEncoder().encodeToString(mergedBytes);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encrypt admin secret", exception);
        }
    }

    @Override
    public String decrypt(String cipherText) {
        this.validateSecretConfigured();
        if (!StringUtils.hasText(cipherText)) {
            return null;
        }
        try {
            byte[] mergedBytes = Base64.getDecoder().decode(cipherText);
            if (mergedBytes.length <= IV_BYTES) {
                throw new BizException(ErrorCode.ADMIN_SECRET_CRYPTO_NOT_CONFIGURED);
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] cipherBytes = new byte[mergedBytes.length - IV_BYTES];
            System.arraycopy(mergedBytes, 0, iv, 0, iv.length);
            System.arraycopy(mergedBytes, iv.length, cipherBytes, 0, cipherBytes.length);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, this.buildSecretKeySpec(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to decrypt admin secret", exception);
        }
    }

    /**
     * 由配置密钥稳定派生 128-bit AES key，避免把原始配置明文直接截断后使用。
     */
    private SecretKeySpec buildSecretKeySpec() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(this.adminSecurityProperties.getEncryptionSecret().getBytes(StandardCharsets.UTF_8));
            byte[] keyBytes = new byte[KEY_BYTES];
            System.arraycopy(digest, 0, keyBytes, 0, KEY_BYTES);
            return new SecretKeySpec(keyBytes, AES_ALGORITHM);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build admin secret key", exception);
        }
    }

    /**
     * 统一校验加密密钥配置，避免在运行中静默使用空密钥。
     */
    private void validateSecretConfigured() {
        if (!StringUtils.hasText(this.adminSecurityProperties.getEncryptionSecret())) {
            throw new BizException(ErrorCode.ADMIN_SECRET_CRYPTO_NOT_CONFIGURED);
        }
    }
}
