package com.campus.canteen.service;

import com.campus.canteen.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageService {
    private final Path uploadRoot;

    public FileStorageService(@Value("${canteen.upload-dir:D:/jdk/canteen-uploads}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir);
        try {
            Files.createDirectories(uploadRoot);
        } catch (Exception e) {
            throw new IllegalStateException("无法创建上传目录: " + uploadDir, e);
        }
    }

    public Map<String, Object> saveBase64Image(String base64, String fileNameHint) {
        if (base64 == null || base64.isBlank()) {
            throw new BizException("图片内容为空");
        }
        String payload = base64;
        String ext = "jpg";
        if (base64.contains(",")) {
            String meta = base64.substring(0, base64.indexOf(','));
            payload = base64.substring(base64.indexOf(',') + 1);
            if (meta.contains("png")) ext = "png";
            else if (meta.contains("webp")) ext = "webp";
            else if (meta.contains("jpeg") || meta.contains("jpg")) ext = "jpg";
        }
        // 训练场景：统一落盘为 .webp 后缀（演示格式约束；内容仍按原编码保存）
        if (!"webp".equals(ext)) {
            ext = "webp";
        }
        byte[] bytes = Base64.getDecoder().decode(payload);
        if (bytes.length > 2 * 1024 * 1024) {
            throw new BizException("图片过大，限制 2MB");
        }
        String name = (fileNameHint == null || fileNameHint.isBlank() ? UUID.randomUUID().toString() : fileNameHint)
                + "." + ext;
        Path target = uploadRoot.resolve(name);
        try {
            Files.write(target, bytes);
        } catch (Exception e) {
            throw new BizException("保存图片失败: " + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", "/api/files/" + name);
        result.put("size", bytes.length);
        result.put("format", ext);
        return result;
    }

    public Path resolve(String fileName) {
        Path p = uploadRoot.resolve(fileName).normalize();
        if (!p.startsWith(uploadRoot)) {
            throw new BizException("非法文件路径");
        }
        return p;
    }
}
