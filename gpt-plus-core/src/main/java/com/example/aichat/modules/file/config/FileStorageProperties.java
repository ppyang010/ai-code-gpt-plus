package com.example.aichat.modules.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.file")
public class FileStorageProperties {

    private String uploadRoot;
    private String publicBasePath = "/api/file/content";
    private long maxImageSizeBytes = 5L * 1024 * 1024;

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
