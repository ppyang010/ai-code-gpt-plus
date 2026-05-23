package com.example.aichat.modules.file.vo;

import lombok.Data;

@Data
public class FileAssetItemVO {

    private Long fileId;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String fileUrl;
    private String thumbnailUrl;
}
