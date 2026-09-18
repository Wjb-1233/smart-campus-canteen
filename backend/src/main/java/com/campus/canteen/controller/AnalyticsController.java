/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.controller;

import com.campus.canteen.common.Result;
import com.campus.canteen.dto.WasteReportRequest;
import com.campus.canteen.service.DashboardService;
import com.campus.canteen.service.NutritionService;
import com.campus.canteen.service.ReportService;
import com.campus.canteen.service.WasteService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 运营分析接口：浪费溯源、档口评分等经营报表。
 *
 * @since 2026-09-15
 */
@RestController
@RequiredArgsConstructor
public class AnalyticsController {
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final NutritionService nutritionService;
    private final DashboardService dashboardService;
    private final ReportService reportService;
    private final WasteService wasteService;

    /**
     * 查询当前用户的营养周报。
     *
     * @return 近 7 天热量、蛋白质与达标率统计
     */
    @GetMapping("/nutrition/weekly")
    public Result<Map<String, Object>> weekly() {
        return Result.ok(nutritionService.personalWeekly());
    }

    /**
     * 查询各院系今日蛋白质达标率，用于管理员端达标率预警。
     *
     * @return 院系达标率与预警列表
     */
    @GetMapping("/nutrition/report/dept-daily")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> deptDaily() {
        return Result.ok(nutritionService.deptDailyReport());
    }

    /**
     * 查询班级营养排行。
     *
     * @return 班级营养排行数据
     */
    @GetMapping("/nutrition/report/class-top")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> classTop() {
        return Result.ok(nutritionService.classTop());
    }

    /**
     * 查询档口营养均衡评分。
     *
     * @return 档口均衡评分数据
     */
    @GetMapping("/nutrition/report/stall-score")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> stallScore() {
        return Result.ok(nutritionService.stallScores());
    }

    /**
     * 查询营养干预建议，供管理员端营养工作台使用。
     *
     * @return 需要干预的院系与建议列表
     */
    @GetMapping("/nutrition/report/intervention")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> intervention() {
        return Result.ok(nutritionService.interventionSuggestions());
    }

    /**
     * 查询运营数据看板概览。
     *
     * @param date      统计日期，默认今天
     * @param canteenId 食堂 ID，可为空
     * @param stallId   档口 ID，可为空
     * @return 交易、热销、在线人数与浪费概览
     */
    @GetMapping("/dashboard/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> overview(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "canteenId", required = false) Long canteenId,
            @RequestParam(name = "stallId", required = false) Long stallId) {
        return Result.ok(dashboardService.overview(date, canteenId, stallId));
    }

    /**
     * 登记剩饭浪费，用于浪费溯源与菜品优化。
     *
     * @param request 浪费登记请求
     * @return 登记结果
     */
    @PostMapping("/waste/report")
    public Result<Map<String, Object>> waste(@Valid @RequestBody WasteReportRequest request) {
        return Result.ok(wasteService.report(request));
    }

    /**
     * 导出日报 Excel。
     *
     * @param stallId 档口 ID，可为空表示全部
     * @return xlsx 文件流
     */
    @GetMapping("/order/statistics/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> export(@RequestParam(name = "stallId", required = false) Long stallId) {
        byte[] data = reportService.dailyExcel(stallId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=daily-report.xlsx")
                .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                .body(data);
    }

    /**
     * 导出日报 PDF。
     *
     * @param stallId 档口 ID，可为空表示全部
     * @return pdf 文件流
     */
    @GetMapping("/order/statistics/export-pdf")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportPdf(@RequestParam(name = "stallId", required = false) Long stallId) {
        byte[] data = reportService.dailyPdf(stallId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=daily-report.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }
}
