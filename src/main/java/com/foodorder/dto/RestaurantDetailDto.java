package com.foodorder.dto;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
public class RestaurantDetailDto {

    private Long restaurantId;
    private String restaurantName;
    private String address;
    private String businessHours;
    private String closedDays;
    private Integer budgetMin;
    private Integer budgetMax;
    private String description;
    private String regionNames;
    private String categoryNames;
    private String imagePath;
    private BigDecimal averageRating;
    private long reviewCount;
}