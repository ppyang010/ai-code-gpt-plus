package com.example.aichat.modules.admin.service.impl;

import com.example.aichat.modules.admin.service.AdminPasswordService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 基于 PBKDF2-HMAC-SHA256 的后台管理员密码哈希服务。
 */
@Service
public class Pbkdf2AdminPasswordService implements AdminPasswordService {

    private static final String HASH_PREFIX = "pbkdf2_sha256";
    private static final int ITERATION_COUNT = 120000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String encode(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        this.secureRandom.nextBytes(salt);
        byte[] hash = this.deriveKey(rawPassword, salt, ITERATION_COUNT);
        return HASH_PREFIX
                + "$" + ITERATION_COUNT
                + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    @Override
    public boolean matches(String rawPassword, String storedHash) {
        if (!StringUtils.hasText(rawPassword) || !StringUtils.hasText(storedHash)) {
            return false;
        }
        String[] segments = storedHash.split("\\$");
        if (segments.length != 4 || !HASH_PREFIX.equals(segments[0])) {
            return false;
        }
        int iterationCount = Integer.parseInt(segments[1]);
        byte[] salt = Base64.getDecoder().decode(segments[2].getBytes(StandardCharsets.UTF_8));
        byte[] expectedHash = Base64.getDecoder().decode(segments[3].getBytes(StandardCharsets.UTF_8));
        byte[] actualHash = this.deriveKey(rawPassword, salt, iterationCount);
        return MessageDigest.isEqual(expectedHash, actualHash);
    }

    /**
     * 使用统一算法和参数导出密码哈希，便于登录校验与后续重置密码复用。
     */
    private byte[] deriveKey(String rawPassword, byte[] salt, int iterationCount) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterationCount, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to derive admin password hash", exception);
        }
    }
}
