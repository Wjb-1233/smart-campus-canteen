package com.campus.canteen.controller;

import com.campus.canteen.common.Result;
import com.campus.canteen.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 操作审计查询：面向食堂管理员与后勤主管，用于追溯关键写操作。
 */
@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
public class AuditLogController {
    private final AuditLogMapper auditLogMapper;

    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<Map<String, Object>>> logs(@RequestParam(name = "limit", defaultValue = "100") int limit) {
        int size = Math.min(Math.max(limit, 1), 500);
        return Result.ok(auditLogMapper.listWithUser(size));
    }
}
