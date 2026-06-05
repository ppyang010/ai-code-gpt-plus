package com.example.aichat.modules.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 文件存储配置，负责描述 Region、Endpoint、Bucket 和访问地址等接入参数。
 */
@Component
@ConfigurationProperties(prefix = "app.file.oss")
public class OssStorageProperties {

    /** Bucket 所在地域，例如 `cn-hangzhou`。 */
    private String region;
    /** SDK 请求使用的 Endpoint，可配置公网域名、专有域或其他受支持地址。 */
    private String endpoint;
    /** 目标 Bucket 名称。 */
    private String bucket;
    /** 对象 Key 的统一前缀，便于区分不同业务目录。 */
    private String objectPrefix = "gpt-plus";
    /** 上传对象时写入的 ACL，私有桶方案下默认写成 private。 */
    private String objectAcl = "private";
    /** 临时签名 URL 的有效期秒数，供前端预览和模型读取私有对象。 */
    private long signedUrlExpireSeconds = 1800L;
    /** 对外访问文件的基础地址，留空时按 Bucket + Endpoint 自动拼接。 */
    private String publicBaseUrl;
    /** 可选的 AccessKey ID；未配置时回退到官方环境变量凭证方式。 */
    private String accessKeyId;
    /** 可选的 AccessKey Secret；未配置时回退到官方环境变量凭证方式。 */
    private String accessKeySecret;

    /**
     * 返回 OSS 所在地域。
     */
    public String getRegion() {
        return region;
    }

    /**
     * 设置 OSS 所在地域。
     */
    public void setRegion(String region) {
        this.region = region;
    }

    /**
     * 返回 OSS Endpoint。
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * 设置 OSS Endpoint。
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * 返回目标 Bucket 名称。
     */
    public String getBucket() {
        return bucket;
    }

    /**
     * 设置目标 Bucket 名称。
     */
    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    /**
     * 返回对象 Key 前缀。
     */
    public String getObjectPrefix() {
        return objectPrefix;
    }

    /**
     * 设置对象 Key 前缀。
     */
    public void setObjectPrefix(String objectPrefix) {
        this.objectPrefix = objectPrefix;
    }

    /**
     * 返回上传对象的 ACL。
     */
    public String getObjectAcl() {
        return objectAcl;
    }

    /**
     * 设置上传对象的 ACL。
     */
    public void setObjectAcl(String objectAcl) {
        this.objectAcl = objectAcl;
    }

    /**
     * 返回临时签名 URL 的有效期秒数。
     */
    public long getSignedUrlExpireSeconds() {
        return signedUrlExpireSeconds;
    }

    /**
     * 设置临时签名 URL 的有效期秒数。
     */
    public void setSignedUrlExpireSeconds(long signedUrlExpireSeconds) {
        this.signedUrlExpireSeconds = signedUrlExpireSeconds;
    }

    /**
     * 返回对外访问文件的基础地址。
     */
    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    /**
     * 设置对外访问文件的基础地址。
     */
    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    /**
     * 返回 AccessKey ID。
     */
    public String getAccessKeyId() {
        return accessKeyId;
    }

    /**
     * 设置 AccessKey ID。
     */
    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    /**
     * 返回 AccessKey Secret。
     */
    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    /**
     * 设置 AccessKey Secret。
     */
    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }
}
