/*
 * Copyright (c) Smart Campus Canteen Project Team 2026-2026. All rights reserved.
 */
package com.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.canteen.common.BizException;
import com.campus.canteen.dto.DishNutritionUpdateRequest;
import com.campus.canteen.entity.Dish;
import com.campus.canteen.entity.NutritionDaily;
import com.campus.canteen.mapper.DishMapper;
import com.campus.canteen.mapper.NutritionDailyMapper;
import com.campus.canteen.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 营养分析业务：日统计、周期报告、干预建议与菜品营养核验。
 *
 * @since 2026-09-15
 */
@Service
@RequiredArgsConstructor
public class NutritionService {
    /** 营养校验阈值区间（单份菜品）。 */
    private static final int CALORIE_MIN = 20;
    private static final int CALORIE_MAX = 1200;
    private static final int PROTEIN_MAX = 80;
    private static final double PROTEIN_RATE_LOW = 60;
    private static final double PROTEIN_RATE_WARN = 80;

    private final NutritionDailyMapper nutritionDailyMapper;
    private final DishMapper dishMapper;
    private final DishService dishService;
    private final ObjectMapper objectMapper;

    /**
     * 菜品营养数据核验清单（管理员端）。
     * 以核验视角给出「已核验 / 待核验 / 数据异常」三类状态与异常原因，
     * 异常菜品可被判为不可信，前端以红色高亮提示。
     *
     * @return 菜品核验清单与各状态数量统计
     */
    public Map<String, Object> dishNutritionAudit() {
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getStatus, 1)
                .orderByAsc(Dish::getNutritionVerified)
                .orderByDesc(Dish::getHeatScore));

        List<Map<String, Object>> rows = new ArrayList<>();
        int verifiedCount = 0;
        int pendingCount = 0;
        int abnormalCount = 0;
        for (Dish d : dishes) {
            Map<String, Object> nutrition = dishService.nutritionOf(d);
            List<String> issues = nutritionIssues(nutrition);
            boolean isVerified = d.getNutritionVerified() != null && d.getNutritionVerified() == 1;
            if (issues.isEmpty() && isVerified) {
                verifiedCount++;
            } else if (issues.isEmpty()) {
                pendingCount++;
            } else {
                abnormalCount++;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dishId", d.getId());
            row.put("name", d.getName());
            row.put("stallId", d.getStallId());
            row.put("mealPeriod", d.getMealPeriod());
            row.put("heatScore", d.getHeatScore());
            row.put("nutrition", nutrition);
            row.put("verified", isVerified);
            row.put("verifiedAt", d.getVerifiedAt());
            row.put("issues", issues);
            row.put("auditStatus", !issues.isEmpty() ? "ABNORMAL" : isVerified ? "VERIFIED" : "PENDING");
            rows.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dishes", rows);
        result.put("total", rows.size());
        result.put("verifiedCount", verifiedCount);
        result.put("pendingCount", pendingCount);
        result.put("abnormalCount", abnormalCount);
        result.put("generatedAt", LocalDate.now().toString());
        return result;
    }

    /**
     * 核验 / 取消核验菜品营养数据（管理员端）。
     * 数据存在异常（缺失热量或蛋白质）时拒绝核验，保证流向学生/老师端的营养数据可信。
     *
     * @param dishId      菜品 ID
     * @param isVerified  true 表示核验通过，false 表示撤销核验
     * @return 核验结果
     * @throws BizException 菜品不存在或存在营养数据异常时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> verifyDishNutrition(Long dishId, boolean isVerified) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            throw new BizException("菜品不存在");
        }
        List<String> issues = nutritionIssues(dishService.nutritionOf(dish));
        if (isVerified && !issues.isEmpty()) {
            throw new BizException("存在营养数据异常，请先修正后再核验：" + String.join("；", issues));
        }
        dish.setNutritionVerified(isVerified ? 1 : 0);
        dish.setVerifiedBy(isVerified ? SecurityUtils.currentUserId() : null);
        dish.setVerifiedAt(isVerified ? LocalDateTime.now() : null);
        dishMapper.updateById(dish);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dishId", dish.getId());
        result.put("name", dish.getName());
        result.put("verified", isVerified);
        result.put("verifiedAt", dish.getVerifiedAt());
        return result;
    }

    /**
     * 修正菜品营养数据，修正后自动退回待核验状态。
     *
     * @param dishId  菜品 ID
     * @param request 营养数据修正请求
     * @return 修正后的营养数据与复检结果
     * @throws BizException 菜品不存在或营养数据序列化失败时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateDishNutrition(Long dishId, DishNutritionUpdateRequest request) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            throw new BizException("菜品不存在");
        }
        Map<String, Object> nutrition = new LinkedHashMap<>();
        nutrition.put("calorie", request.getCalorie());
        nutrition.put("protein", request.getProtein());
        nutrition.put("fat", request.getFat());
        nutrition.put("carb", request.getCarb());
        try {
            dish.setNutritionJson(objectMapper.writeValueAsString(nutrition));
        } catch (JsonProcessingException e) {
            throw new BizException("营养数据序列化失败");
        }
        // 数据被修改后需重新核验，避免学生/老师端继续展示未经确认的数据
        dish.setNutritionVerified(0);
        dish.setVerifiedBy(null);
        dish.setVerifiedAt(null);
        dishMapper.updateById(dish);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dishId", dish.getId());
        result.put("name", dish.getName());
        result.put("nutrition", nutrition);
        result.put("issues", nutritionIssues(nutrition));
        result.put("verified", false);
        result.put("remark", request.getRemark());
        return result;
    }

    /**
     * 查询当前用户的营养周报：近 7 天热量、蛋白质与达标预警。
     *
     * @return 近 7 天营养曲线与预警提示
     */
    public Map<String, Object> personalWeekly() {
        Long userId = SecurityUtils.currentUserId();
        LocalDate start = LocalDate.now().minusDays(6);
        List<NutritionDaily> list = nutritionDailyMapper.selectList(new LambdaQueryWrapper<NutritionDaily>()
                .eq(NutritionDaily::getUserId, userId)
                .ge(NutritionDaily::getStatDate, start)
                .orderByAsc(NutritionDaily::getStatDate));

        List<String> days = new ArrayList<>();
        List<Integer> calories = new ArrayList<>();
        List<BigDecimal> proteins = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            days.add(d.toString());
            NutritionDaily hit = list.stream().filter(x -> d.equals(x.getStatDate())).findFirst().orElse(null);
            calories.add(hit == null || hit.getCalorie() == null ? 0 : hit.getCalorie());
            proteins.add(hit == null || hit.getProtein() == null ? BigDecimal.ZERO : hit.getProtein());
        }

        BigDecimal latestProtein = proteins.get(proteins.size() - 1);
        String warning = latestProtein.compareTo(BigDecimal.valueOf(56)) < 0
                ? "蛋白质摄入偏低，建议增加鸡胸/豆制品"
                : "蛋白质摄入良好";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("calories", calories);
        result.put("proteins", proteins);
        result.put("proteinWarning", warning);
        result.put("heatmap", calories);
        return result;
    }

    /**
     * 查询各院系今日蛋白质达标率，并输出低于阈值的红色预警。
     *
     * @return 院系达标率列表与预警列表
     */
    public Map<String, Object> deptDailyReport() {
        List<Map<String, Object>> rows = nutritionDailyMapper.deptProteinRateToday();
        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            double rate = toDouble(row.get("avgProteinRate"));
            if (rate < PROTEIN_RATE_WARN) {
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("department", row.get("department"));
                w.put("avgProteinRate", rate);
                w.put("alert", "【红色预警】院系「" + row.get("department") + "」今日平均蛋白质达标率仅 "
                        + rate + "%，低于" + (int) PROTEIN_RATE_WARN + "%阈值");
                warnings.add(w);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("departments", rows);
        result.put("alerts", warnings);
        result.put("generatedAt", LocalDate.now().toString());
        return result;
    }

    /**
     * 查询班级营养排行。
     *
     * @return 班级营养排行列表
     */
    public Map<String, Object> classTop() {
        List<Map<String, Object>> rows = nutritionDailyMapper.classNutritionTop();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topList", rows);
        result.put("generatedAt", LocalDate.now().toString());
        return result;
    }

    /**
     * 查询各档口营养均衡评分。
     *
     * @return 档口均衡评分列表
     */
    public Map<String, Object> stallScores() {
        List<Map<String, Object>> rows = nutritionDailyMapper.stallNutritionScores();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stalls", rows);
        result.put("generatedAt", LocalDate.now().toString());
        return result;
    }

    /**
     * 管理员端「干预建议」：根据院系达标率与档口营养均衡分自动生成分级建议，
     * 让营养监管不止于「看数据」，而是能直接输出可执行结论。
     *
     * @return 按级别排序的干预建议与高级别建议数量
     */
    public Map<String, Object> interventionSuggestions() {
        List<Map<String, Object>> suggestions = new ArrayList<>();

        // 1) 院系维度：达标率低于阈值给出红色干预
        for (Map<String, Object> dept : nutritionDailyMapper.deptProteinRateToday()) {
            double rate = toDouble(dept.get("avgProteinRate"));
            String name = String.valueOf(dept.get("department"));
            String content = deptInterventionContent(name, rate);
            if (content != null) {
                suggestions.add(suggestion("院系", name, rate < PROTEIN_RATE_LOW ? "HIGH" : "MID", content));
            }
        }

        // 2) 档口维度：均衡分与人均蛋白质
        for (Map<String, Object> stall : nutritionDailyMapper.stallNutritionScores()) {
            double score = toDouble(stall.get("balanceScore"));
            double avgProtein = toDouble(stall.get("avgProtein"));
            String name = String.valueOf(stall.get("stallName"));
            String content = stallInterventionContent(name, score);
            if (content != null) {
                suggestions.add(suggestion("档口", name, score < PROTEIN_RATE_LOW ? "HIGH" : "MID", content));
            }
            if (avgProtein > 0 && avgProtein < 18) {
                suggestions.add(suggestion("档口", name, "MID",
                        "档口「" + name + "」人均蛋白质仅 " + avgProtein + "g，建议推出「高蛋白加餐」选项"));
            }
        }

        if (suggestions.isEmpty()) {
            suggestions.add(suggestion("全校", "全部档口", "LOW",
                    "各院系与档口营养结构总体达标，建议保持现有菜谱并持续监测"));
        }
        suggestions.sort(Comparator.comparing(s -> String.valueOf(s.get("level"))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("suggestions", suggestions);
        result.put("highCount", suggestions.stream().filter(s -> "HIGH".equals(s.get("level"))).count());
        result.put("generatedAt", LocalDate.now().toString());
        return result;
    }

    private String deptInterventionContent(String name, double rate) {
        if (rate < PROTEIN_RATE_LOW) {
            return "院系「" + name + "」蛋白质达标率仅 " + rate
                    + "%，建议联合辅导员推送高蛋白套餐并加强营养科普";
        }
        if (rate < PROTEIN_RATE_WARN) {
            return "院系「" + name + "」达标率 " + rate + "%，建议在档口增加豆制品与鸡蛋类窗口";
        }
        return null;
    }

    private String stallInterventionContent(String name, double score) {
        if (score < PROTEIN_RATE_LOW) {
            return "档口「" + name + "」营养均衡分仅 " + score
                    + "，建议增加高蛋白低脂菜品（鸡胸沙拉、藜麦碗）并下调油炸类占比";
        }
        if (score < 75) {
            return "档口「" + name + "」均衡分 " + score + "，建议优化荤素配比与主食份量";
        }
        return null;
    }

    /** 营养数据异常规则：缺失/越界即判为异常。 */
    private List<String> nutritionIssues(Map<String, Object> nutrition) {
        List<String> issues = new ArrayList<>();
        if (nutrition == null || nutrition.isEmpty()) {
            issues.add("营养数据缺失");
            return issues;
        }
        if (nutrition.get("calorie") == null) {
            issues.add("缺少热量字段");
        }
        if (nutrition.get("protein") == null) {
            issues.add("缺少蛋白质字段");
        }
        int calorie = toInt(nutrition.get("calorie"));
        int protein = toInt(nutrition.get("protein"));
        if (nutrition.get("calorie") != null && (calorie < CALORIE_MIN || calorie > CALORIE_MAX)) {
            issues.add("热量 " + calorie + " kcal 超出合理区间 " + CALORIE_MIN + "~" + CALORIE_MAX);
        }
        if (nutrition.get("protein") != null && (protein < 0 || protein > PROTEIN_MAX)) {
            issues.add("蛋白质 " + protein + " g 超出合理区间 0~" + PROTEIN_MAX);
        }
        return issues;
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(value)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Map<String, Object> suggestion(String scope, String target, String level, String content) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("scope", scope);
        row.put("target", target);
        row.put("level", level);
        row.put("content", content);
        return row;
    }

    private double toDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0d;
    }
}
