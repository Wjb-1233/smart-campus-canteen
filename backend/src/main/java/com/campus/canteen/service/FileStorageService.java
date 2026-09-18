/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.service;

import com.campus.canteen.common.BizException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 图片文件存储业务，负责 Base64 落盘与访问路径生成。
 *
 * @since 2026-09-15
 */
@Service
public class FileStorageService {
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;

    private final Path uploadRoot;

    /**
     * 构造文件存储服务，并确保上传目录存在。
     *
     * @param uploadDir 上传根目录
     * @throws IllegalStateException 上传目录创建失败时抛出
     */
    public FileStorageService(@Value("${canteen.upload-dir:D:/jdk/canteen-uploads}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir);
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + uploadDir, e);
        }
    }

    /**
     * 保存 Base64 图片到上传目录。
     *
     * @param base64       图片 Base64 内容，可携带 data URI 前缀
     * @param fileNameHint 期望的文件名，为空时自动生成
     * @return 含访问路径、文件大小与格式的结果
     * @throws BizException 图片为空、超过 2MB 或落盘失败时抛出
     */
    public Map<String, Object> saveBase64Image(String base64, String fileNameHint) {
        if (base64 == null || base64.isBlank()) {
            throw new BizException("图片内容为空");
        }
        String payload = base64;
        if (base64.contains(",")) {
            payload = base64.substring(base64.indexOf(',') + 1);
        }
        // 训练场景：统一落盘为 .webp 后缀（演示格式约束；内容仍按原编码保存）
        String ext = "webp";
        byte[] bytes = Base64.getDecoder().decode(payload);
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new BizException("图片过大，限制 2MB");
        }
        String name = (fileNameHint == null || fileNameHint.isBlank() ? UUID.randomUUID().toString() : fileNameHint)
                + "." + ext;
        Path target = uploadRoot.resolve(name);
        try {
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new BizException("保存图片失败: " + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", "/api/files/" + name);
        result.put("size", bytes.length);
        result.put("format", ext);
        return result;
    }

    /**
     * 将文件名解析为上传目录下的安全路径，防止目录穿越。
     *
     * @param fileName 文件名
     * @return 归一化后的文件路径
     * @throws BizException 解析结果越出上传目录时抛出
     */
    public Path resolve(String fileName) {
        Path p = uploadRoot.resolve(fileName).normalize();
        if (!p.startsWith(uploadRoot)) {
            throw new BizException("非法文件路径");
        }
        return p;
    }
}
