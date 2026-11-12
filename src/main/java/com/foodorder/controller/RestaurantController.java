package com.foodorder.controller;

import com.foodorder.dto.RestaurantListDto;
import com.foodorder.service.RestaurantService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.foodorder.dto.RestaurantDetailDto;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller
public class RestaurantController {

    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @GetMapping("/restaurants")
    public String list(
            @RequestParam(name = "keyword", defaultValue = "") String keyword,
            @RequestParam(name = "region", defaultValue = "") String region,
            @RequestParam(name = "category", defaultValue = "") String category,
            Model model
    ) {
        List<RestaurantListDto> restaurants = restaurantService.search(keyword, region, category);

        model.addAttribute("restaurants", restaurants);
        model.addAttribute("keyword", keyword);
        model.addAttribute("region", region);
        model.addAttribute("category", category);

        return "restaurants/list";
    }
    @GetMapping("/restaurants/{restaurantId:[0-9]+}")
    public String detail(
            @PathVariable("restaurantId") Long restaurantId,
            Model model
    ) {
        RestaurantDetailDto restaurant = restaurantService.findDetailById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "お店が見つかりませんでした。"
                ));

        model.addAttribute("restaurant", restaurant);
        model.addAttribute("menuItems",
                restaurantService.findMenuItemsByRestaurantId(restaurantId));
        return "restaurants/detail";
    }
}