package com.example.aichat.modules.file.service.impl;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.PresignOptions;
import com.aliyun.sdk.service.oss2.credentials.Credentials;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.EnvironmentVariableCredentialsProvider;
import com.aliyun.sdk.service.oss2.models.GetObjectRequest;
import com.aliyun.sdk.service.oss2.models.PresignResult;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.modules.file.config.FileStorageProperties;
import com.example.aichat.modules.file.config.OssStorageProperties;
import com.example.aichat.modules.file.entity.FileAssetDO;
import com.example.aichat.modules.file.mapper.FileAssetMapper;
import com.example.aichat.modules.file.service.FileAssetService;
import com.example.aichat.modules.file.vo.FileAssetItemVO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 聊天附件存储服务，负责图片校验、附件元数据落库，以及本地存储和阿里云 OSS 两种落盘方式的切换。
 */
@Service
public class FileAssetServiceImpl implements FileAssetService {

    private static final Logger log = LoggerFactory.getLogger(FileAssetServiceImpl.class);
    private static final Integer FILE_STATUS_ACTIVE = 1;
    private static final String BIZ_TYPE_CHAT_IMAGE = "chat_image";
    /** 约定的 OSS 存储类型编码，对应 `app.file.storage-type=oss`。 */
    private static final String STORAGE_TYPE_OSS = "oss";
    /** 阿里云 OSS V4 预签名 URL 最长只允许 604800 秒，即 7 天。 */
    private static final long MAX_OSS_SIGNED_URL_EXPIRE_SECONDS = 604800L;
    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");
    /** 当前支持的 OSS 对象 ACL 枚举，避免配置拼写错误导致线上请求失败。 */
    private static final Set<String> ALLOWED_OSS_OBJECT_ACLS = Set.of("default", "private", "public-read", "public-read-write");

    private final FileAssetMapper fileAssetMapper;
    private final FileStorageProperties fileStorageProperties;
    /** OSS 相关配置，启用远端存储时用于拼接对象 Key、上传地址和访问 URL。 */
    private final OssStorageProperties ossStorageProperties;

    /**
     * 初始化附件存储服务，并注入本地与 OSS 两套配置来源。
     */
    public FileAssetServiceImpl(
            FileAssetMapper fileAssetMapper,
            FileStorageProperties fileStorageProperties,
            OssStorageProperties ossStorageProperties
    ) {
        this.fileAssetMapper = fileAssetMapper;
        this.fileStorageProperties = fileStorageProperties;
        this.ossStorageProperties = ossStorageProperties;
    }

    /**
     * 上传聊天图片：根据存储配置决定写入本地文件系统还是阿里云 OSS，再落库统一附件元数据。
     */
    @Override
    public FileAssetItemVO uploadImage(Long userId, MultipartFile file) {
        this.validateImageFile(file);
        String originalFileName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "image";
        String extension = this.extractExtension(originalFileName);
        String contentType = this.normalizeContentType(file.getContentType(), extension);
        String storageRelativePath = this.buildStorageRelativePath("chat-image", extension);
        Path targetFile = null;
        String storagePath;
        String fileUrl = null;
        String signedUrl = null;

        try {
            if (this.isOssStorage()) {
                storagePath = this.buildOssObjectKey(storageRelativePath);
                this.uploadToOss(storagePath, file, contentType);
                fileUrl = this.buildOssStoredFileUrl(storagePath);
                signedUrl = this.buildOssPresignedReadUrl(storagePath);
            } else {
                targetFile = this.storeToLocal(storageRelativePath, file);
                storagePath = targetFile.toString();
            }

            FileAssetDO entity = new FileAssetDO();
            entity.setUserId(userId);
            entity.setBizType(BIZ_TYPE_CHAT_IMAGE);
            entity.setFileName(originalFileName);
            entity.setContentType(contentType);
            entity.setFileSize(file.getSize());
            entity.setStoragePath(storagePath);
            entity.setFileUrl(fileUrl);
            entity.setSignedUrl(signedUrl);
            entity.setStatus(FILE_STATUS_ACTIVE);
            this.fileAssetMapper.insert(entity);

            if (!StringUtils.hasText(entity.getFileUrl())) {
                entity.setFileUrl(this.buildPublicFileUrl(entity.getId()));
                this.fileAssetMapper.updateById(entity);
            }
            log.info(
                    "Uploaded chat image userId={} fileId={} fileSize={} contentType={} storageType={}",
                    userId,
                    entity.getId(),
                    entity.getFileSize(),
                    entity.getContentType(),
                    this.fileAssetServiceType()
            );
            return this.toItem(entity);
        } catch (IOException exception) {
            this.deleteQuietly(targetFile);
            log.warn("Failed to store uploaded image for userId={}", userId, exception);
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        } catch (BizException exception) {
            this.deleteQuietly(targetFile);
            throw exception;
        } catch (Exception exception) {
            this.deleteQuietly(targetFile);
            throw exception;
        }
    }

    @Override
    public List<FileAssetItemVO> requireOwnedCompletedAssets(Long userId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }

        List<Long> normalizedFileIds = fileIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (normalizedFileIds.isEmpty()) {
            return List.of();
        }

        List<FileAssetDO> assets = this.loadOwnedActiveAssets(userId, normalizedFileIds);

        Map<Long, FileAssetDO> assetMap = assets.stream()
                .collect(Collectors.toMap(FileAssetDO::getId, asset -> asset));

        return normalizedFileIds.stream()
                .map(assetMap::get)
                .map(this::toItem)
                .toList();
    }

    @Override
    public FileAssetDO getPublicAsset(Long fileId) {
        FileAssetDO asset = fileAssetMapper.selectById(fileId);
        if (asset == null || !Objects.equals(asset.getStatus(), FILE_STATUS_ACTIVE)) {
            throw new BizException(ErrorCode.FILE_ASSET_NOT_FOUND);
        }
        return asset;
    }

    /**
     * 生成当前可访问的附件地址：OSS 私有桶返回新鲜签名 URL，本地文件返回稳定业务地址。
     */
    @Override
    public String buildReadUrl(FileAssetDO asset) {
        if (asset == null) {
            throw new BizException(ErrorCode.FILE_ASSET_NOT_FOUND);
        }
        if (this.isOssAsset(asset)) {
            if (StringUtils.hasText(asset.getSignedUrl())) {
                return asset.getSignedUrl();
            }
            return this.buildOssPresignedReadUrl(asset.getStoragePath());
        }
        return this.buildPublicFileUrl(asset.getId());
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.FILE_IMAGE_EMPTY);
        }
        if (file.getSize() > fileStorageProperties.getMaxImageSizeBytes()) {
            throw new BizException(ErrorCode.FILE_IMAGE_TOO_LARGE);
        }

        String extension = this.extractExtension(file.getOriginalFilename());
        String contentType = this.normalizeContentType(file.getContentType(), extension);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension) || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new BizException(ErrorCode.FILE_IMAGE_TYPE_NOT_SUPPORTED);
        }
    }

    /**
     * 生成统一的相对存储路径，让本地目录结构和 OSS Object Key 保持同一套分层规则。
     */
    private String buildStorageRelativePath(String bizFolder, String extension) {
        LocalDate today = LocalDate.now();
        String storedFileName = UUID.randomUUID().toString().replace("-", "") + extension;
        return bizFolder
                + "/" + today.getYear()
                + "/" + String.format("%02d", today.getMonthValue())
                + "/" + String.format("%02d", today.getDayOfMonth())
                + "/" + storedFileName;
    }

    /**
     * 将文件保存到本地目录，供本地开发模式继续使用原有文件读取接口。
     */
    private Path storeToLocal(String storageRelativePath, MultipartFile file) throws IOException {
        Path targetFile = this.resolveUploadRoot().resolve(storageRelativePath);
        Files.createDirectories(targetFile.getParent());
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return targetFile;
    }

    /**
     * 将文件直接上传到阿里云 OSS，并保持对象 ACL 与私有桶方案一致。
     */
    private void uploadToOss(String objectKey, MultipartFile file, String contentType) throws IOException {
        OSSClient client = this.buildOssClient();
        try {
            PutObjectRequest request = PutObjectRequest.newBuilder()
                    .bucket(this.ossStorageProperties.getBucket().trim())
                    .key(objectKey)
                    .contentType(contentType)
                    .objectAcl(this.resolveOssObjectAcl())
                    .body(BinaryData.fromBytes(file.getBytes()))
                    .build();
            client.putObject(request);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Failed to upload image to OSS objectKey={}", objectKey, exception);
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "图片上传到阿里云 OSS 失败，请稍后重试");
        } finally {
            this.closeOssClientQuietly(client);
        }
    }

    /**
     * 构造 OSS 客户端；静态 AK 配置优先，未配置时回退到官方环境变量凭证方式。
     */
    private OSSClient buildOssClient() {
        if (!StringUtils.hasText(this.ossStorageProperties.getRegion())
                || !StringUtils.hasText(this.ossStorageProperties.getEndpoint())
                || !StringUtils.hasText(this.ossStorageProperties.getBucket())) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "阿里云 OSS 配置不完整，请检查 region、endpoint 和 bucket");
        }

        return OSSClient.newBuilder()
                .region(this.ossStorageProperties.getRegion().trim())
                .endpoint(this.ossStorageProperties.getEndpoint().trim())
                .credentialsProvider(this.buildOssCredentialsProvider())
                .build();
    }

    /**
     * 解析 OSS 凭证来源：优先使用配置文件中的静态 AK，未配置时回退到官方环境变量方案。
     */
    private CredentialsProvider buildOssCredentialsProvider() {
        String accessKeyId = this.ossStorageProperties.getAccessKeyId();
        String accessKeySecret = this.ossStorageProperties.getAccessKeySecret();
        if (StringUtils.hasText(accessKeyId) || StringUtils.hasText(accessKeySecret)) {
            if (!StringUtils.hasText(accessKeyId) || !StringUtils.hasText(accessKeySecret)) {
                throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "阿里云 OSS AccessKey 配置不完整，请同时配置 accessKeyId 和 accessKeySecret");
            }
            return new CredentialsProvider() {
                /**
                 * 按当前配置返回 OSS 所需的长期 AccessKey 凭证。
                 */
                @Override
                public Credentials getCredentials() {
                    return new Credentials(accessKeyId.trim(), accessKeySecret.trim());
                }
            };
        }
        return new EnvironmentVariableCredentialsProvider();
    }

    /**
     * 解析并校验本次上传使用的对象 ACL；私有桶方案默认写成 private，避免再依赖对象公网读配置。
     */
    private String resolveOssObjectAcl() {
        String objectAcl = StringUtils.hasText(this.ossStorageProperties.getObjectAcl())
                ? this.ossStorageProperties.getObjectAcl().trim().toLowerCase(Locale.ROOT)
                : "private";
        if (!ALLOWED_OSS_OBJECT_ACLS.contains(objectAcl)) {
            throw new BizException(
                    ErrorCode.FILE_UPLOAD_FAILED,
                    "阿里云 OSS 对象 ACL 配置不合法，仅支持 default/private/public-read/public-read-write"
            );
        }
        return objectAcl;
    }

    /**
     * 为 OSS 私有对象生成新的 GET 临时签名 URL，供前端预览和外部模型在有效期内访问。
     */
    private String buildOssPresignedReadUrl(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new BizException(ErrorCode.FILE_ASSET_NOT_FOUND, "阿里云 OSS Object Key 不能为空");
        }
        long expireSeconds = this.ossStorageProperties.getSignedUrlExpireSeconds();
        if (expireSeconds <= 0) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "阿里云 OSS 签名 URL 有效期必须大于 0 秒");
        }
        if (expireSeconds > MAX_OSS_SIGNED_URL_EXPIRE_SECONDS) {
            // OSS 服务端会直接拒绝超过 7 天的 V4 预签名 URL，这里提前收敛，避免上传阶段报 500。
            expireSeconds = MAX_OSS_SIGNED_URL_EXPIRE_SECONDS;
        }

        OSSClient client = this.buildOssClient();
        try {
            GetObjectRequest request = GetObjectRequest.newBuilder()
                    .bucket(this.ossStorageProperties.getBucket().trim())
                    .key(objectKey)
                    .build();
            PresignResult presignResult = client.presign(
                    request,
                    PresignOptions.newBuilder().expiration(Duration.ofSeconds(expireSeconds)).build()
            );
            return presignResult.url();
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Failed to presign image read url objectKey={}", objectKey, exception);
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "图片签名地址生成失败，请稍后重试");
        } finally {
            this.closeOssClientQuietly(client);
        }
    }

    /**
     * 按统一前缀拼装 OSS Object Key，避免不同环境或业务目录互相覆盖。
     */
    private String buildOssObjectKey(String storageRelativePath) {
        String objectPrefix = this.ossStorageProperties.getObjectPrefix();
        if (!StringUtils.hasText(objectPrefix)) {
            return storageRelativePath;
        }
        String normalizedPrefix = objectPrefix.trim();
        normalizedPrefix = normalizedPrefix.endsWith("/") ? normalizedPrefix.substring(0, normalizedPrefix.length() - 1) : normalizedPrefix;
        return normalizedPrefix + "/" + storageRelativePath;
    }

    /**
     * 安静关闭 OSS 客户端，避免上传后的资源句柄泄漏影响后续请求。
     */
    private void closeOssClientQuietly(OSSClient client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception exception) {
            log.warn("Failed to close OSS client", exception);
        }
    }

    /**
     * 判断当前是否启用了 OSS 存储模式。
     */
    private boolean isOssStorage() {
        return STORAGE_TYPE_OSS.equalsIgnoreCase(this.fileStorageProperties.getStorageType());
    }

    /**
     * 返回当前生效的文件存储类型，仅用于日志补充，便于区分本地与 OSS 上传链路。
     */
    private String fileAssetServiceType() {
        return this.isOssStorage() ? STORAGE_TYPE_OSS : "local";
    }

    private Path resolveUploadRoot() {
        String configuredRoot = fileStorageProperties.getUploadRoot();
        if (StringUtils.hasText(configuredRoot)) {
            return Paths.get(configuredRoot);
        }
        return Paths.get(System.getProperty("user.dir"), ".local-storage", "files");
    }

    /**
     * 返回稳定业务读取地址，供前端页面继续通过 `/api/file/content/{id}` 访问附件内容。
     */
    private String buildPublicFileUrl(Long fileId) {
        String publicBasePath = fileStorageProperties.getPublicBasePath();
        if (!StringUtils.hasText(publicBasePath)) {
            publicBasePath = "/api/file/content";
        }
        return publicBasePath.endsWith("/") ? publicBasePath + fileId : publicBasePath + "/" + fileId;
    }

    /**
     * 构造要落库的原始 OSS 地址，确保数据库保存的是对象真实位置而不是业务代理地址。
     */
    private String buildOssStoredFileUrl(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "阿里云 OSS Object Key 不能为空");
        }

        String publicBaseUrl = this.ossStorageProperties.getPublicBaseUrl();
        if (StringUtils.hasText(publicBaseUrl)) {
            return this.joinUrl(publicBaseUrl, objectKey);
        }

        String endpoint = this.ossStorageProperties.getEndpoint();
        String bucket = this.ossStorageProperties.getBucket();
        if (!StringUtils.hasText(endpoint) || !StringUtils.hasText(bucket)) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "阿里云 OSS 访问地址配置不完整，请检查 endpoint、bucket 或 publicBaseUrl");
        }

        String normalizedEndpoint = endpoint.trim();
        String scheme = "https://";
        if (normalizedEndpoint.startsWith("http://")) {
            scheme = "http://";
            normalizedEndpoint = normalizedEndpoint.substring("http://".length());
        } else if (normalizedEndpoint.startsWith("https://")) {
            normalizedEndpoint = normalizedEndpoint.substring("https://".length());
        }
        normalizedEndpoint = normalizedEndpoint.endsWith("/") ? normalizedEndpoint.substring(0, normalizedEndpoint.length() - 1) : normalizedEndpoint;
        return scheme + bucket.trim() + "." + normalizedEndpoint + "/" + objectKey;
    }

    /**
     * 把附件实体转换成前端展示对象，缩略图当前直接复用主文件地址。
     */
    private FileAssetItemVO toItem(FileAssetDO asset) {
        FileAssetItemVO item = new FileAssetItemVO();
        item.setFileId(asset.getId());
        item.setFileName(asset.getFileName());
        item.setContentType(asset.getContentType());
        item.setFileSize(asset.getFileSize());
        item.setFileUrl(this.buildPublicFileUrl(asset.getId()));
        item.setThumbnailUrl(this.buildPublicFileUrl(asset.getId()));
        return item;
    }

    /**
     * 统一读取用户自己的有效附件，供消息发送和模型多模态组装复用。
     */
    private List<FileAssetDO> loadOwnedActiveAssets(Long userId, List<Long> normalizedFileIds) {
        List<FileAssetDO> assets = fileAssetMapper.selectList(new LambdaQueryWrapper<FileAssetDO>()
                .eq(FileAssetDO::getUserId, userId)
                .eq(FileAssetDO::getStatus, FILE_STATUS_ACTIVE)
                .in(FileAssetDO::getId, normalizedFileIds));

        if (assets.size() != normalizedFileIds.size()) {
            throw new BizException(ErrorCode.CHAT_ATTACHMENT_NOT_FOUND);
        }
        return assets;
    }

    /**
     * 判断附件是否来自 OSS 对象存储；当前实现用“非绝对路径的 object key”与本地绝对文件路径区分两类记录。
     */
    private boolean isOssAsset(FileAssetDO asset) {
        if (asset == null || !StringUtils.hasText(asset.getStoragePath())) {
            return false;
        }
        return !Path.of(asset.getStoragePath()).isAbsolute();
    }

    /**
     * 统一拼接基础 URL 和对象 Key，避免手工处理时出现双斜杠或漏斜杠。
     */
    private String joinUrl(String baseUrl, String path) {
        String normalizedBaseUrl = baseUrl.trim();
        normalizedBaseUrl = normalizedBaseUrl.endsWith("/") ? normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1) : normalizedBaseUrl;
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        return normalizedBaseUrl + "/" + normalizedPath;
    }

    private String extractExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType, String extension) {
        if (StringUtils.hasText(contentType)) {
            return contentType.toLowerCase(Locale.ROOT);
        }

        Map<String, String> contentTypeByExtension = new HashMap<>();
        contentTypeByExtension.put(".jpg", "image/jpeg");
        contentTypeByExtension.put(".jpeg", "image/jpeg");
        contentTypeByExtension.put(".png", "image/png");
        contentTypeByExtension.put(".webp", "image/webp");
        return contentTypeByExtension.getOrDefault(extension, "application/octet-stream");
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("Failed to delete temporary upload file {}", path, exception);
        }
    }
}
