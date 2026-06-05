package com.example.aichat.modules.file.controller;

import com.example.aichat.common.dto.CommonResponse;
import com.example.aichat.modules.file.entity.FileAssetDO;
import com.example.aichat.modules.file.service.FileAssetService;
import com.example.aichat.modules.file.vo.FileAssetItemVO;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 聊天附件控制器，负责承接前端上传请求并兼容本地/OSS 两种读取方式。
 */
@RestController
@RequestMapping("/api/file")
public class FileAssetController {

    private final FileAssetService fileAssetService;

    /**
     * 注入附件服务，供上传和读取接口复用同一套存储逻辑。
     */
    public FileAssetController(FileAssetService fileAssetService) {
        this.fileAssetService = fileAssetService;
    }

    /**
     * 接收前端图片直传请求，并把附件元数据回传给聊天输入区做预览和发送。
     */
    @PostMapping(value = "/upload/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<FileAssetItemVO> uploadImage(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestPart("file") MultipartFile file
    ) {
        return CommonResponse.success(this.fileAssetService.uploadImage(this.resolveUserId(userId), file));
    }

    /**
     * 兼容读取附件内容：OSS 记录直接重定向到对象地址，本地记录继续走服务端文件流读取。
     */
    @GetMapping("/content/{fileId}")
    public ResponseEntity<?> readContent(@PathVariable("fileId") Long fileId) throws IOException {
        FileAssetDO asset = this.fileAssetService.getPublicAsset(fileId);
        if (this.shouldRedirectToRemoteFile(asset)) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(this.fileAssetService.buildReadUrl(asset)))
                    .build();
        }

        Path filePath = Path.of(asset.getStoragePath());
        Resource resource = new FileSystemResource(filePath);

        String contentType = StringUtils.hasText(asset.getContentType())
                ? asset.getContentType()
                : Files.probeContentType(filePath);

        return ResponseEntity.ok()
                .contentType(StringUtils.hasText(contentType)
                        ? MediaType.parseMediaType(contentType)
                        : MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + asset.getFileName() + "\"")
                .body(resource);
    }

    /**
     * 判断附件是否已经落到远端对象存储；如果是，则读取接口只负责把浏览器导向最终对象地址。
     */
    private boolean shouldRedirectToRemoteFile(FileAssetDO asset) {
        if (asset == null || !StringUtils.hasText(asset.getStoragePath()) || !StringUtils.hasText(asset.getFileUrl())) {
            return false;
        }
        return !Path.of(asset.getStoragePath()).isAbsolute();
    }

    /**
     * 当前仍使用临时用户头兜底本地联调，后续登录鉴权接入后再统一替换。
     */
    private Long resolveUserId(Long userId) {
        return userId != null ? userId : 1L;
    }
}
