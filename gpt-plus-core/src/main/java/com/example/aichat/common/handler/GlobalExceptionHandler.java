package com.example.aichat.common.handler;

import com.example.aichat.common.dto.CommonResponse;
import com.example.aichat.common.exception.BizException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public CommonResponse<Object> handleBizException(BizException exception) {
        return CommonResponse.failure(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public CommonResponse<Object> handleException(Exception exception) {
        return CommonResponse.failure(500, exception.getMessage());
    }
}
