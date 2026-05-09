package com.example.aichat.infrastructure.ai;

public class ChatModelClientException extends RuntimeException {

    private final Integer httpStatus;
    private final String errorCode;
    private final String responsePayload;

    public ChatModelClientException(String message, Integer httpStatus, String errorCode, String responsePayload) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.responsePayload = responsePayload;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getResponsePayload() {
        return responsePayload;
    }
}
