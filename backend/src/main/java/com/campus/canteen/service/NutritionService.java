package com.campus.canteen.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.canteen.common.BizException;
import com.campus.canteen.dto.DishNutritionUpdateRequest;
import com.campus.canteen.entity.Dish;
import com.campus.canteen.entity.NutritionDaily;
import com.campus.canteen.mapper.DishMapper;
import com.campus.canteen.mapper.NutritionDailyMapper;
import com.campus.canteen.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NutritionService {
    private final NutritionDailyMapper nutritionDailyMapper;
    private final DishMapper dishMapper;
    private final DishService dishService;
    private final ObjectMapper objectMapper;

    /** 营养校验阈值区间（单份菜品）。 */
    private static final int CALORIE_MIN = 20;
    private static final int CALORIE_MAX = 1200;
    private static final int PROTEIN_MAX = 80;

    /**
     * 营养师端：菜品营养数据核验清单。
     * 以营养师视角给出「已核验 / 待核验 / 数据异常」三类状态与异常原因，
     * 异常菜品可被判为不可信，前端以红色高亮提示。
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
            boolean verified = d.getNutritionVerified() != null && d.getNutritionVerified() == 1;
            if (issues.isEmpty() && verified) {
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
            row.put("verified", verified);
            row.put("verifiedAt", d.getVerifiedAt());
            row.put("issues", issues);
            row.put("auditStatus", !issues.isEmpty() ? "ABNORMAL" : verified ? "VERIFIED" : "PENDING");
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
     * 营养师核验 / 取消核验菜品营养数据。
     * 数据存在异常（缺失热量或蛋白质）时拒绝核验，保证流向学生的营养数据可信。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> verifyDishNutrition(Long dishId, boolean verified) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            throw new BizException("菜品不存在");
        }
        List<String> issues = nutritionIssues(dishService.nutritionOf(dish));
        if (verified && !issues.isEmpty()) {
            throw new BizException("存在营养数据异常，请先修正后再核验：" + String.join("；", issues));
        }
        dish.setNutritionVerified(verified ? 1 : 0);
        dish.setVerifiedBy(verified ? SecurityUtils.currentUserId() : null);
        dish.setVerifiedAt(verified ? LocalDateTime.now() : null);
        dishMapper.updateById(dish);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dishId", dish.getId());
        result.put("name", dish.getName());
        result.put("verified", verified);
        result.put("verifiedAt", dish.getVerifiedAt());
        return result;
    }

    /**
     * 营养师修正菜品营养数据（列入核验闭环：修正 -> 重新核验 -> 学生端展示可信标识）。
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
        } catch (Exception e) {
            throw new BizException("营养数据序列化失败");
        }
        // 数据被修改后需营养师重新核验，避免学生端继续展示未经确认的数据
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
            calories.add(hit == null ? 0 : hit.getCalorie());
            proteins.add(hit == null ? BigDecimal.ZERO : hit.getProtein());
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

    public Map<String, Object> deptDailyReport() {
        List<Map<String, Object>> rows = nutritionDailyMapper.deptProteinRateToday();
        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object rateObj = row.get("avgProteinRate");
            double rate = rateObj == null ? 0 : ((Number) rateObj).doubleValue();
            if (rate < 80) {
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("department", row.get("department"));
                w.put("avgProteinRate", rate);
                w.put("alert", "【红色预警】院系「" + row.get("department") + "」今日平均蛋白质达标率仅 "
                        + rate + "%，低于80%阈值");
                warnings.add(w);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("departments", rows);
        result.put("alerts", warnings);
        result.put("generatedAt", LocalDate.now().toString());
        return result;
    }

    public Map<String, Object> classTop() {
        List<Map<String, Object>> rows = nutritionDailyMapper.classNutritionTop();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topList", rows);
        result.put("generatedAt", LocalDate.now().toString());
        return result;
    }

    public Map<String, Object> stallScores() {
        List<Map<String, Object>> rows = nutritionDailyMapper.stallNutritionScores();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stalls", rows);
        result.put("generatedAt", LocalDate.now().toString());
        return result;
    }

    /**
     * 营养师端「干预建议」：根据院系达标率与档口营养均衡分自动生成分级建议，
     * 让营养师端不止于「看数据」，而是能直接输出可执行结论。
     */
    public Map<String, Object> interventionSuggestions() {
        List<Map<String, Object>> suggestions = new ArrayList<>();

        // 1) 院系维度：达标率低于阈值给出红色干预
        List<Map<String, Object>> depts = nutritionDailyMapper.deptProteinRateToday();
        for (Map<String, Object> dept : depts) {
            double rate = toDouble(dept.get("avgProteinRate"));
            String name = String.valueOf(dept.get("department"));
            if (rate < 60) {
                suggestions.add(suggestion("院系", name, "HIGH",
                        "院系「" + name + "」蛋白质达标率仅 " + rate + "%，建议联合辅导员推送高蛋白套餐并加强营养科普"));
            } else if (rate < 80) {
                suggestions.add(suggestion("院系", name, "MID",
                        "院系「" + name + "」达标率 " + rate + "%，建议在档口增加豆制品与鸡蛋类窗口"));
            }
        }

        // 2) 档口维度：均衡分与人均蛋白质
        List<Map<String, Object>> stalls = nutritionDailyMapper.stallNutritionScores();
        for (Map<String, Object> stall : stalls) {
            double score = toDouble(stall.get("balanceScore"));
            double avgProtein = toDouble(stall.get("avgProtein"));
            String name = String.valueOf(stall.get("stallName"));
            if (score < 60) {
                suggestions.add(suggestion("档口", name, "HIGH",
                        "档口「" + name + "」营养均衡分仅 " + score + "，建议增加高蛋白低脂菜品（鸡胸沙拉、藜麦碗）并下调油炸类占比"));
            } else if (score < 75) {
                suggestions.add(suggestion("档口", name, "MID",
                        "档口「" + name + "」均衡分 " + score + "，建议优化荤素配比与主食份量"));
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
