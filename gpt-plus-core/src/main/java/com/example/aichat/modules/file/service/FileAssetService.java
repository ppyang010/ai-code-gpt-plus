package com.example.aichat.modules.file.service;

import com.example.aichat.modules.file.entity.FileAssetDO;
import com.example.aichat.modules.file.vo.FileAssetItemVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 聊天附件服务接口，统一暴露上传、归属校验和附件读取能力。
 */
public interface FileAssetService {

    /**
     * 上传单张聊天图片并返回前端回显所需的附件信息。
     */
    FileAssetItemVO uploadImage(Long userId, MultipartFile file);

    /**
     * 校验附件归属与状态，并按请求顺序返回可回显的附件信息。
     */
    List<FileAssetItemVO> requireOwnedCompletedAssets(Long userId, List<Long> fileIds);

    /**
     * 读取对外可访问的附件记录，用于图片内容接口。
     */
    FileAssetDO getPublicAsset(Long fileId);

    /**
     * 为附件生成当前可访问的读取地址；OSS 私有桶场景下会返回临时签名 URL。
     */
    String buildReadUrl(FileAssetDO asset);
}
