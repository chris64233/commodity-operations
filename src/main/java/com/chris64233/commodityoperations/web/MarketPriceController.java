package com.chris64233.commodityoperations.web;

import com.chris64233.commodityoperations.dto.MarketPriceView;
import com.chris64233.commodityoperations.dto.PublishMarketPriceRequest;
import com.chris64233.commodityoperations.service.MarketPriceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market-prices")
public class MarketPriceController {

    private final MarketPriceService marketPriceService;

    public MarketPriceController(MarketPriceService marketPriceService) {
        this.marketPriceService = marketPriceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MarketPriceView publish(@Valid @RequestBody PublishMarketPriceRequest request) {
        return marketPriceService.publish(request);
    }

    @GetMapping
    public List<MarketPriceView> list() {
        return marketPriceService.list();
    }
}
