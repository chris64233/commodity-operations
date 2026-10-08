package com.chris64233.commodityoperations.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class PricingController {

    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    public record RegisterContractRequest(
            @NotBlank String commodity,
            @NotNull Direction direction,
            @NotNull @Positive BigDecimal quantity,
            @NotBlank String currency,
            @NotNull BigDecimal provisionalPrice,
            @NotNull Instant pricingWindowStart,
            @NotNull Instant pricingWindowEnd,
            @NotNull Set<String> allowedSources) {
    }

    public record RegisterMarketPriceRequest(
            @NotBlank String source,
            @NotBlank String commodity,
            @NotBlank String currency,
            @NotNull BigDecimal price,
            long priceVersion,
            @NotNull Instant validFrom,
            @NotNull Instant validTo) {
    }

    public record FixRequest(
            @NotBlank String externalFixingNo,
            @NotNull @Positive BigDecimal quantity,
            @NotBlank String source,
            long priceVersion,
            @NotNull BigDecimal basis) {
    }

    @PostMapping("/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractView registerContract(@Valid @RequestBody RegisterContractRequest request) {
        Contract contract = pricingService.registerContract(
                request.commodity(), request.direction(), request.quantity(), request.currency(),
                request.provisionalPrice(), request.pricingWindowStart(), request.pricingWindowEnd(),
                request.allowedSources());
        return pricingService.getContractView(contract.getId());
    }

    @PostMapping("/market-prices")
    @ResponseStatus(HttpStatus.CREATED)
    public MarketPrice registerMarketPrice(@Valid @RequestBody RegisterMarketPriceRequest request) {
        return pricingService.registerMarketPrice(
                request.source(), request.commodity(), request.currency(), request.price(),
                request.priceVersion(), request.validFrom(), request.validTo());
    }

    @PostMapping("/contracts/{id}/fixings")
    @ResponseStatus(HttpStatus.CREATED)
    public PriceFixing fix(@PathVariable Long id, @Valid @RequestBody FixRequest request) {
        return pricingService.fix(id, request.externalFixingNo(), request.quantity(),
                request.source(), request.priceVersion(), request.basis());
    }

    @PostMapping("/contracts/{id}/final-settlement")
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementEntry finalizeSettlement(@PathVariable Long id) {
        return pricingService.finalizeSettlement(id);
    }

    @GetMapping("/contracts/{id}")
    public ContractView getContract(@PathVariable Long id) {
        return pricingService.getContractView(id);
    }
}
