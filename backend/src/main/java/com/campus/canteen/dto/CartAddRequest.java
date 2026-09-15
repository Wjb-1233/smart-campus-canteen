package com.campus.canteen.dto;

import lombok.Data;

@Data
public class CartAddRequest {
    private Long dishId;
    private Integer quantity = 1;
    private String mealPeriod;
}
