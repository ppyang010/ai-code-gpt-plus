package com.example.aichat.modules.admin.service;

/**
 * 后台管理员密码哈希服务。
 */
public interface AdminPasswordService {

    /**
     * 将明文密码编码成可落库的哈希格式。
     */
    String encode(String rawPassword);

    /**
     * 校验明文密码是否匹配当前哈希。
     */
    boolean matches(String rawPassword, String storedHash);
}
