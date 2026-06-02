package com.wgxhl.recipe.controller;

import com.wgxhl.recipe.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public ApiResponse<Map<String, String>> health() {
        Map<String, String> data = new HashMap<>();
        data.put("status", "UP");
        data.put("service", "wgxhl-recipe");
        return ApiResponse.success(data);
    }
}
