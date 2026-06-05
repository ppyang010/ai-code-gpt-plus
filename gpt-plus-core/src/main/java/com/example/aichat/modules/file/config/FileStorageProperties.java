package com.example.aichat.modules.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 通用文件存储配置，负责描述本地/OSS 存储模式切换和本地回退参数。
 */
@Component
@ConfigurationProperties(prefix = "app.file")
public class FileStorageProperties {

    /** 文件存储类型，默认使用本地文件系统，可切换为 oss。 */
    private String storageType = "local";
    private String uploadRoot;
    private String publicBasePath = "/api/file/content";
    private long maxImageSizeBytes = 5L * 1024 * 1024;

    /**
     * 返回当前文件存储类型。
     */
    public String getStorageType() {
        return storageType;
    }

    /**
     * 设置当前文件存储类型。
     */
    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public String getUploadRoot() {
        return uploadRoot;
    }

    public void setUploadRoot(String uploadRoot) {
        this.uploadRoot = uploadRoot;
    }

    public String getPublicBasePath() {
        return publicBasePath;
    }

    public void setPublicBasePath(String publicBasePath) {
        this.publicBasePath = publicBasePath;
    }

    public long getMaxImageSizeBytes() {
        return maxImageSizeBytes;
    }

    public void setMaxImageSizeBytes(long maxImageSizeBytes) {
        this.maxImageSizeBytes = maxImageSizeBytes;
    }
}
