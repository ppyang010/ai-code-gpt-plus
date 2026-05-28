package com.example.aichat.common.handler;

import com.example.aichat.common.dto.CommonResponse;
import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常的 message 已经面向前端展示，因此这里不再二次包装文案。
     */
    @ExceptionHandler(BizException.class)
    public CommonResponse<Object> handleBizException(BizException exception, HttpServletRequest request) {
        log.warn(
                "Biz exception on {} {} code={} message={}",
                requestMethod(request),
                requestUri(request),
                exception.getCode(),
                exception.getMessage()
        );
        return CommonResponse.failure(exception.getCode(), exception.getMessage());
    }

    /**
     * Bean Validation 失败时尽量保留字段名，方便前端和调试日志快速定位是哪一个入参不合法。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResponse<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn(
                "Validation failed on {} {} message={}",
                requestMethod(request),
                requestUri(request),
                withFallback(message, ErrorCode.INVALID_PARAM.getMessage())
        );
        return CommonResponse.failure(ErrorCode.INVALID_PARAM.getCode(), withFallback(message, ErrorCode.INVALID_PARAM.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public CommonResponse<Object> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining("; "));
        log.warn(
                "Constraint violation on {} {} message={}",
                requestMethod(request),
                requestUri(request),
                withFallback(message, ErrorCode.INVALID_PARAM.getMessage())
        );
        return CommonResponse.failure(ErrorCode.INVALID_PARAM.getCode(), withFallback(message, ErrorCode.INVALID_PARAM.getMessage()));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public CommonResponse<Object> handleRequestException(Exception exception, HttpServletRequest request) {
        log.warn(
                "Request parse failed on {} {} type={} message={}",
                requestMethod(request),
                requestUri(request),
                exception.getClass().getSimpleName(),
                exception.getMessage()
        );
        return CommonResponse.failure(ErrorCode.REQUEST_BODY_INVALID.getCode(), ErrorCode.REQUEST_BODY_INVALID.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public CommonResponse<Object> handleException(
            Exception exception,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (isEventStreamResponse(response)) {
            log.warn("Unhandled SSE exception on {} {}", requestMethod(request), requestUri(request), exception);
            return null;
        }
        log.error("Unhandled exception on {} {}", requestMethod(request), requestUri(request), exception);
        return CommonResponse.failure(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + " " + fieldError.getDefaultMessage();
    }

    private String withFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean isEventStreamResponse(HttpServletResponse response) {
        return response != null
                && response.getContentType() != null
                && response.getContentType().contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    private String requestMethod(HttpServletRequest request) {
        return request == null ? "UNKNOWN" : request.getMethod();
    }

    private String requestUri(HttpServletRequest request) {
        return request == null ? "unknown" : request.getRequestURI();
    }
}
