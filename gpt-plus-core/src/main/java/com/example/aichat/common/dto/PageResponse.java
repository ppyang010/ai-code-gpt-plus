package com.example.aichat.common.dto;

import java.util.Collections;
import java.util.List;
import lombok.Data;

@Data
public class PageResponse<T> {

    private List<T> list;
    private Long total;
    private Integer pageNo;
    private Integer pageSize;

    public static <T> PageResponse<T> empty(Integer pageNo, Integer pageSize) {
        PageResponse<T> response = new PageResponse<>();
        response.setList(Collections.emptyList());
        response.setTotal(0L);
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        return response;
    }
}
