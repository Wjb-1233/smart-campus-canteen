package com.campus.canteen.controller;

import com.campus.canteen.common.Result;
import com.campus.canteen.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class FileController {
    private final FileStorageService fileStorageService;

    @PostMapping("/files/upload-base64")
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<Map<String, Object>> upload(@RequestBody Map<String, String> body) {
        return Result.ok(fileStorageService.saveBase64Image(body.get("base64"), body.get("name")));
    }

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
