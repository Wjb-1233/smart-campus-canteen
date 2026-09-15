package com.campus.canteen.dto;

import lombok.Data;

import java.util.List;

@Data
public class HealthUpdateRequest {
    private List<String> allergies;
    private List<String> dietTaboo;
    private Integer targetCalorie;
    private Integer targetProtein;
    private String religionTag;
}
