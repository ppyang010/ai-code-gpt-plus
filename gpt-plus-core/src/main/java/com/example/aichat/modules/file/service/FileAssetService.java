package com.example.aichat.modules.file.service;

import com.example.aichat.modules.file.entity.FileAssetDO;
import com.example.aichat.modules.file.vo.FileAssetItemVO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface FileAssetService {

    FileAssetItemVO uploadImage(Long userId, MultipartFile file);

    List<FileAssetItemVO> requireOwnedCompletedAssets(Long userId, List<Long> fileIds);

    FileAssetDO getPublicAsset(Long fileId);
}
