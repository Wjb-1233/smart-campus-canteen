package com.campus.canteen.controller;

import com.campus.canteen.common.Result;
import com.campus.canteen.entity.Dish;
import com.campus.canteen.entity.NutritionTag;
import com.campus.canteen.service.DishService;
import com.campus.canteen.service.RecommendService;
import com.campus.canteen.vo.DishVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dish")
@RequiredArgsConstructor
public class DishController {
    private final DishService dishService;
    private final RecommendService recommendService;

    @GetMapping("/admin/list")
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<List<DishVO>> adminList() {
        return Result.ok(dishService.adminList());
    }

    @GetMapping("/search")
    public Result<List<DishVO>> search(@RequestParam(name = "keyword", required = false) String keyword,
                                       @RequestParam(name = "mealPeriod", required = false) String mealPeriod,
                                       @RequestParam(name = "tags", required = false) String tags) {
        List<String> tagList = tags == null || tags.isBlank()
                ? List.of()
                : Arrays.stream(tags.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return Result.ok(dishService.search(keyword, mealPeriod, tagList));
    }

    @GetMapping("/{id}")
    public Result<DishVO> detail(@PathVariable(name = "id") Long id) {
        return Result.ok(dishService.detail(id));
    }

    @GetMapping("/tags")
    public Result<List<NutritionTag>> tags() {
        return Result.ok(dishService.allTags());
    }

    @GetMapping("/stalls")
    public Result<List<Map<String, Object>>> stalls() {
        return Result.ok(dishService.stallOptions());
    }

    @GetMapping("/recommend")
    public Result<List<?>> recommend(@RequestParam(name = "mealPeriod", required = false) String mealPeriod,
                                     @RequestParam(name = "limit", defaultValue = "6") int limit) {
        return Result.ok(recommendService.recommend(mealPeriod, limit));
    }

    @GetMapping("/stock/alerts")
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<List<Map<String, Object>>> alerts(@RequestParam(name = "threshold", defaultValue = "10") int threshold) {
        return Result.ok(dishService.lowStockAlerts(threshold));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<Dish> save(@RequestBody Dish dish) {
        return Result.ok(dishService.saveOrUpdate(dish));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<Dish> update(@RequestBody Dish dish) {
        return Result.ok(dishService.saveOrUpdate(dish));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('STALL','ADMIN')")
    public Result<Void> delete(@PathVariable(name = "id") Long id) {
        dishService.delete(id);
        return Result.ok();
    }
}
