package com.example.aichat.modules.file.controller;

import com.example.aichat.common.dto.CommonResponse;
import com.example.aichat.modules.file.entity.FileAssetDO;
import com.example.aichat.modules.file.service.FileAssetService;
import com.example.aichat.modules.file.vo.FileAssetItemVO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
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

@RestController
@RequestMapping("/api/file")
public class FileAssetController {

    private final FileAssetService fileAssetService;

    public FileAssetController(FileAssetService fileAssetService) {
        this.fileAssetService = fileAssetService;
    }

    @PostMapping(value = "/upload/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse<FileAssetItemVO> uploadImage(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestPart("file") MultipartFile file
    ) {
        return CommonResponse.success(fileAssetService.uploadImage(resolveUserId(userId), file));
    }

    @GetMapping("/content/{fileId}")
    public ResponseEntity<Resource> readContent(@PathVariable("fileId") Long fileId) throws IOException {
        FileAssetDO asset = fileAssetService.getPublicAsset(fileId);
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

    private Long resolveUserId(Long userId) {
        return userId != null ? userId : 1L;
    }
}
