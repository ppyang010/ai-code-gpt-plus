package com.example.aichat.modules.admin.support;

import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;

/**
 * 后台请求上下文持有器。
 */
public final class AdminAuthContextHolder {

    private static final ThreadLocal<AdminAuthContext> CONTEXT_HOLDER = new ThreadLocal<>();

    private AdminAuthContextHolder() {
    }

    /**
     * 写入当前线程的后台管理员上下文。
     */
    public static void setContext(AdminAuthContext context) {
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 读取当前线程的后台管理员上下文。
     */
    public static AdminAuthContext getContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 读取当前线程的后台管理员上下文；缺失时按未登录处理。
     */
    public static AdminAuthContext getRequiredContext() {
        AdminAuthContext context = getContext();
        if (context == null || context.getAdminUserId() == null) {
            throw new BizException(ErrorCode.ADMIN_AUTH_REQUIRED);
        }
        return context;
    }

    /**
     * 清理当前线程的后台管理员上下文，避免线程复用导致串号。
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }
}
