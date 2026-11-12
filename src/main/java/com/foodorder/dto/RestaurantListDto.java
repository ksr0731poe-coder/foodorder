package com.foodorder.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestaurantListDto {

    private Long restaurantId;
    private String restaurantName;
    private String description;
    private String regionNames;
    private String categoryNames;
    private String imagePath;
}