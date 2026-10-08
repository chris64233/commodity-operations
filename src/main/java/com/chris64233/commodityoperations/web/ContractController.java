package com.chris64233.commodityoperations.web;

import com.chris64233.commodityoperations.dto.ContractView;
import com.chris64233.commodityoperations.dto.RegisterContractRequest;
import com.chris64233.commodityoperations.dto.SubmitPricingRequest;
import com.chris64233.commodityoperations.service.ContractService;
import com.chris64233.commodityoperations.service.PricingService;
import com.chris64233.commodityoperations.service.SettlementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractService;
    private final PricingService pricingService;
    private final SettlementService settlementService;

    public ContractController(ContractService contractService,
                              PricingService pricingService,
                              SettlementService settlementService) {
        this.contractService = contractService;
        this.pricingService = pricingService;
        this.settlementService = settlementService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContractView register(@Valid @RequestBody RegisterContractRequest request) {
        return contractService.register(request);
    }

    @GetMapping("/{contractNo}")
    public ContractView get(@PathVariable String contractNo) {
        return contractService.get(contractNo);
    }

    @PostMapping("/{id}/pricings")
    public ResponseEntity<ContractView> submitPricing(
            @PathVariable Long id, @Valid @RequestBody SubmitPricingRequest request) {
        ContractView view = pricingService.submitPricing(id, request);
        return ResponseEntity.ok(view);
    }

    @PostMapping("/{id}/final-settlement")
    public ContractView finalSettle(@PathVariable Long id) {
        return settlementService.finalSettle(id);
    }
}
