package com.example.aichat.modules.admin.service;

import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.modules.admin.dto.AdminApiCallLogPageQuery;
import com.example.aichat.modules.admin.vo.AdminApiCallLogItemVO;

/**
 * 后台模型调用日志服务。
 */
public interface AdminApiCallLogService {

    /**
     * 分页查询模型调用日志。
     */
    PageResponse<AdminApiCallLogItemVO> pageLogs(AdminApiCallLogPageQuery query);

    /**
     * 查询单条模型调用日志详情。
     */
    AdminApiCallLogItemVO getLogDetail(Long logId);
}
