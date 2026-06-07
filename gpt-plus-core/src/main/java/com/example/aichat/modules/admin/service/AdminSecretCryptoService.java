package com.example.aichat.modules.admin.service;

/**
 * 后台敏感字段加解密服务。
 */
public interface AdminSecretCryptoService {

    /**
     * 加密明文敏感信息。
     */
    String encrypt(String plainText);

    /**
     * 解密密文敏感信息。
     */
    String decrypt(String cipherText);
}
