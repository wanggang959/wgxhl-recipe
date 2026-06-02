package com.wgxhl.recipe.controller;

import com.wgxhl.recipe.common.ApiResponse;
import com.wgxhl.recipe.service.CosService;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/file")
public class FileController {

    private final CosService cosService;

    public FileController(CosService cosService) {
        this.cosService = cosService;
    }

    @PostMapping("/upload")
    public ApiResponse<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "recipe") String folder
    ) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.fail("请选择上传文件");
        }
        String safeFolder = StringUtils.hasText(folder)
                ? folder.replace("\\", "/").replaceAll("^/+", "").replaceAll("/+$", "")
                : "recipe";
        try {
            String url = cosService.upload(file, safeFolder);
            return ApiResponse.success("上传成功", url);
        } catch (IOException e) {
            return ApiResponse.fail("上传失败: " + e.getMessage());
        }
    }
}
