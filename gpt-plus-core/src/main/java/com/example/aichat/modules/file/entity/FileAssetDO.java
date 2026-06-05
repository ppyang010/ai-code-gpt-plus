package com.example.aichat.modules.file.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.model.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件附件实体，统一承载 OSS 对象位置、原始地址和可直接访问的签名地址。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("file_asset")
public class FileAssetDO extends BaseDO {

    @TableField("user_id")
    private Long userId;

    @TableField("biz_type")
    private String bizType;

    @TableField("file_name")
    private String fileName;

    @TableField("content_type")
    private String contentType;

    @TableField("file_size")
    private Long fileSize;

    @TableField("storage_path")
    private String storagePath;

    @TableField("file_url")
    private String fileUrl;

    /** 可直接访问的 OSS 签名地址，测试阶段可配置为较长有效期。 */
    @TableField("signed_url")
    private String signedUrl;

    @TableField("status")
    private Integer status;
}
