package com.campus.canteen.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DishVO {
    private Long id;
    private String name;
    private BigDecimal price;
    private Integer stock;
    private String stockHint;
    private String imageUrl;
    private String mealPeriod;
    private Integer heatScore;
    private Long stallId;
    private String stallName;
    private Map<String, Object> nutrition;
    private List<String> tags;
    private List<String> tagCodes;
    /** 推荐理由（个性化推荐引擎输出的可解释文本）。 */
    private String reason;
    /** 营养师是否已核验该菜品的营养数据。 */
    private Boolean nutritionVerified;
}
