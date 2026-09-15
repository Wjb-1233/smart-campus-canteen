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
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AnalyticsController {
    private final NutritionService nutritionService;
    private final DashboardService dashboardService;
    private final ReportService reportService;
    private final WasteService wasteService;

    @GetMapping("/nutrition/weekly")
    public Result<Map<String, Object>> weekly() {
        return Result.ok(nutritionService.personalWeekly());
    }

    @GetMapping("/nutrition/report/dept-daily")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> deptDaily() {
        return Result.ok(nutritionService.deptDailyReport());
    }

    @GetMapping("/nutrition/report/class-top")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> classTop() {
        return Result.ok(nutritionService.classTop());
    }

    @GetMapping("/nutrition/report/stall-score")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> stallScore() {
        return Result.ok(nutritionService.stallScores());
    }

    /** 营养干预建议：营养师端工作台使用。 */
    @GetMapping("/nutrition/report/intervention")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> intervention() {
        return Result.ok(nutritionService.interventionSuggestions());
    }

    @GetMapping("/dashboard/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> overview(
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "canteenId", required = false) Long canteenId,
            @RequestParam(name = "stallId", required = false) Long stallId) {
        return Result.ok(dashboardService.overview(date, canteenId, stallId));
    }

    @PostMapping("/waste/report")
    public Result<Map<String, Object>> waste(@Valid @RequestBody WasteReportRequest request) {
        return Result.ok(wasteService.report(request));
    }

    @GetMapping("/order/statistics/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> export(@RequestParam(name = "stallId", required = false) Long stallId) {
        byte[] data = reportService.dailyExcel(stallId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=daily-report.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

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
