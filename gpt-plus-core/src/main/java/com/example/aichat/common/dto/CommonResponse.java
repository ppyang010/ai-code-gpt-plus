package com.example.aichat.common.dto;

import lombok.Data;

@Data
public class CommonResponse<T> {

    private Integer code;
    private String message;
    private T data;

    public static <T> CommonResponse<T> success(T data) {
        CommonResponse<T> response = new CommonResponse<>();
        response.setCode(0);
        response.setMessage("success");
        response.setData(data);
        return response;
    }

    public static <T> CommonResponse<T> failure(Integer code, String message) {
        CommonResponse<T> response = new CommonResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
}
