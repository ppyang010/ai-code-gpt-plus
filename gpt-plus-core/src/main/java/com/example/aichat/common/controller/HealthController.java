package com.example.aichat.common.controller;

import com.example.aichat.common.dto.CommonResponse;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public CommonResponse<Map<String, Object>> health() {
        return CommonResponse.success(Map.of(
                "status", "UP",
                "service", "gpt-plus-core",
                "checkedAt", LocalDateTime.now()
        ));
    }
}
