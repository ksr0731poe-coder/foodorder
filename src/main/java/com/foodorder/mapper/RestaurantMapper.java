package com.foodorder.mapper;

import java.util.List;
import com.foodorder.dto.RestaurantMenuDto;
import com.foodorder.dto.RestaurantDetailDto;
import com.foodorder.dto.RestaurantListDto;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RestaurantMapper {

    List<RestaurantListDto> search(
            @Param("keyword") String keyword,
            @Param("region") String region,
            @Param("category") String category
    );
    RestaurantDetailDto findDetailById(@Param("restaurantId") Long restaurantId);
    List<RestaurantMenuDto> findMenuItemsByRestaurantId(
            @Param("restaurantId") Long restaurantId
    );
}