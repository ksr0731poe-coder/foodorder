package com.foodorder.service;

import com.foodorder.dto.RestaurantListDto;
import com.foodorder.mapper.RestaurantMapper;
import org.springframework.stereotype.Service;
import com.foodorder.dto.RestaurantDetailDto;
import java.util.Optional;
import com.foodorder.dto.RestaurantMenuDto;

import java.util.List;

@Service
public class RestaurantService {

    private final RestaurantMapper restaurantMapper;

    public RestaurantService(RestaurantMapper restaurantMapper) {
        this.restaurantMapper = restaurantMapper;
    }

    public List<RestaurantListDto> search(String keyword, String region, String category) {
        return restaurantMapper.search(
                normalize(keyword),
                normalize(region),
                normalize(category)
        );
    }
    public Optional<RestaurantDetailDto> findDetailById(Long restaurantId) {
        return Optional.ofNullable(restaurantMapper.findDetailById(restaurantId));
    }
    public List<RestaurantMenuDto> findMenuItemsByRestaurantId(Long restaurantId) {
        return restaurantMapper.findMenuItemsByRestaurantId(restaurantId);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}