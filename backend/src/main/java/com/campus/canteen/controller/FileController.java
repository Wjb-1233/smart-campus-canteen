/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.controller;

import com.campus.canteen.common.Result;
import com.campus.canteen.service.FileStorageService;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.util.Map;

/**
 * 文件接口：Base64 图片上传与静态资源访问。
 *
 * @since 2026-09-15
 */
@RestController
@RequiredArgsConstructor
public class FileController {
    private final FileStorageService fileStorageService;

    /**
     * 接收 Base64 图片并落盘，返回可访问的相对路径。
     *
     * @param body 请求体，包含 base64 内容与原始文件名
     * @return 含访问路径的响应
     */
    @PostMapping("/files/upload-base64")
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<Map<String, Object>> upload(@RequestBody Map<String, String> body) {
        return Result.ok(fileStorageService.saveBase64Image(body.get("base64"), body.get("name")));
    }

    /**
     * 按文件名读取已上传的图片资源。
     *
     * @param name 文件名
     * @return 图片资源响应；文件不存在时返回 404
     * @throws Exception 文件探测失败时抛出
     */
    @GetMapping("/files/{name}")
    public ResponseEntity<Resource> download(@PathVariable(name = "name") String name) throws Exception {
        var path = fileStorageService.resolve(name);
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType == null ? "application/octet-stream" : contentType))
                .body(new FileSystemResource(path));
    }
}
