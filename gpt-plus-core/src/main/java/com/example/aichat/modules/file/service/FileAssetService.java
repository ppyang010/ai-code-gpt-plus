package com.example.aichat.modules.file.service;

import com.example.aichat.modules.file.entity.FileAssetDO;
import com.example.aichat.modules.file.vo.FileAssetItemVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

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
     * 将用户自己的图片附件转换为可直接发给外部模型的 `data:` URL。
     */
    List<String> buildOwnedImageDataUrls(Long userId, List<Long> fileIds);

    /**
     * 读取对外可访问的附件记录，用于图片内容接口。
     */
    FileAssetDO getPublicAsset(Long fileId);
}
