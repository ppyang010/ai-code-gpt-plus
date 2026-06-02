package com.example.aichat.modules.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.modules.file.config.FileStorageProperties;
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
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileAssetServiceImpl implements FileAssetService {

    private static final Logger log = LoggerFactory.getLogger(FileAssetServiceImpl.class);
    private static final Integer FILE_STATUS_ACTIVE = 1;
    private static final String BIZ_TYPE_CHAT_IMAGE = "chat_image";
    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");

    private final FileAssetMapper fileAssetMapper;
    private final FileStorageProperties fileStorageProperties;

    public FileAssetServiceImpl(FileAssetMapper fileAssetMapper, FileStorageProperties fileStorageProperties) {
        this.fileAssetMapper = fileAssetMapper;
        this.fileStorageProperties = fileStorageProperties;
    }

    @Override
    public FileAssetItemVO uploadImage(Long userId, MultipartFile file) {
        validateImageFile(file);

        String originalFileName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "image";
        String extension = extractExtension(originalFileName);
        LocalDate today = LocalDate.now();
        Path targetDirectory = resolveUploadRoot()
                .resolve("chat-image")
                .resolve(String.valueOf(today.getYear()))
                .resolve(String.format("%02d", today.getMonthValue()))
                .resolve(String.format("%02d", today.getDayOfMonth()));
        String storedFileName = UUID.randomUUID().toString().replace("-", "") + extension;
        Path targetFile = targetDirectory.resolve(storedFileName);

        try {
            Files.createDirectories(targetDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }

            FileAssetDO entity = new FileAssetDO();
            entity.setUserId(userId);
            entity.setBizType(BIZ_TYPE_CHAT_IMAGE);
            entity.setFileName(originalFileName);
            entity.setContentType(normalizeContentType(file.getContentType(), extension));
            entity.setFileSize(file.getSize());
            entity.setStoragePath(targetFile.toString());
            entity.setStatus(FILE_STATUS_ACTIVE);
            fileAssetMapper.insert(entity);

            entity.setFileUrl(buildPublicFileUrl(entity.getId()));
            fileAssetMapper.updateById(entity);
            log.info(
                    "Uploaded chat image userId={} fileId={} fileSize={} contentType={}",
                    userId,
                    entity.getId(),
                    entity.getFileSize(),
                    entity.getContentType()
            );
            return toItem(entity);
        } catch (IOException exception) {
            deleteQuietly(targetFile);
            log.warn("Failed to store uploaded image for userId={}", userId, exception);
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        } catch (Exception exception) {
            deleteQuietly(targetFile);
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
    public List<String> buildOwnedImageDataUrls(Long userId, List<Long> fileIds) {
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

        List<FileAssetDO> ownedAssets = this.loadOwnedActiveAssets(userId, normalizedFileIds);
        Map<Long, FileAssetDO> assetMap = ownedAssets.stream()
                .collect(Collectors.toMap(FileAssetDO::getId, asset -> asset));

        return normalizedFileIds.stream()
                .map(assetMap::get)
                .map(this::toModelImageDataUrl)
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

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.FILE_IMAGE_EMPTY);
        }
        if (file.getSize() > fileStorageProperties.getMaxImageSizeBytes()) {
            throw new BizException(ErrorCode.FILE_IMAGE_TOO_LARGE);
        }

        String extension = extractExtension(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType(), extension);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension) || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new BizException(ErrorCode.FILE_IMAGE_TYPE_NOT_SUPPORTED);
        }
    }

    private Path resolveUploadRoot() {
        String configuredRoot = fileStorageProperties.getUploadRoot();
        if (StringUtils.hasText(configuredRoot)) {
            return Paths.get(configuredRoot);
        }
        return Paths.get(System.getProperty("user.dir"), ".local-storage", "files");
    }

    private String buildPublicFileUrl(Long fileId) {
        String publicBasePath = fileStorageProperties.getPublicBasePath();
        if (!StringUtils.hasText(publicBasePath)) {
            publicBasePath = "/api/file/content";
        }
        return publicBasePath.endsWith("/") ? publicBasePath + fileId : publicBasePath + "/" + fileId;
    }

    private FileAssetItemVO toItem(FileAssetDO asset) {
        FileAssetItemVO item = new FileAssetItemVO();
        item.setFileId(asset.getId());
        item.setFileName(asset.getFileName());
        item.setContentType(asset.getContentType());
        item.setFileSize(asset.getFileSize());
        item.setFileUrl(asset.getFileUrl());
        item.setThumbnailUrl(asset.getFileUrl());
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
     * 将图片文件内容编码成 `data:` URL，避免外部模型访问不到本机 `/api/file/content/**` 地址。
     */
    private String toModelImageDataUrl(FileAssetDO asset) {
        if (asset == null || !StringUtils.hasText(asset.getStoragePath())) {
            throw new BizException(ErrorCode.CHAT_ATTACHMENT_NOT_FOUND);
        }
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(asset.getStoragePath()));
            String mimeType = StringUtils.hasText(asset.getContentType()) ? asset.getContentType() : "image/png";
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException exception) {
            log.warn("Failed to read chat image for model request fileId={}", asset.getId(), exception);
            throw new BizException(ErrorCode.CHAT_ATTACHMENT_READ_FAILED);
        }
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
