package com.chris64233.commodityoperations.web;

import com.chris64233.commodityoperations.dto.ContractRequests.CreateContractRequest;
import com.chris64233.commodityoperations.dto.ContractRequests.RegisterDeliveryRequest;
import com.chris64233.commodityoperations.dto.ContractRequests.ReverseDeliveryRequest;
import com.chris64233.commodityoperations.dto.ContractViews.ContractView;
import com.chris64233.commodityoperations.dto.ContractViews.DeliveryView;
import com.chris64233.commodityoperations.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContractView createContract(@Valid @RequestBody CreateContractRequest request) {
        return contractService.createContract(request);
    }

    @GetMapping("/{contractId}")
    public ContractView getContract(@PathVariable Long contractId) {
        return contractService.getContract(contractId);
    }

    @PostMapping("/{contractId}/deliveries")
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryView registerDelivery(@PathVariable Long contractId,
                                         @Valid @RequestBody RegisterDeliveryRequest request) {
        return contractService.registerDelivery(contractId, request);
    }

    @PostMapping("/{contractId}/deliveries/{deliveryId}/reversals")
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryView reverseDelivery(@PathVariable Long contractId,
                                        @PathVariable Long deliveryId,
                                        @Valid @RequestBody ReverseDeliveryRequest request) {
        return contractService.reverseDelivery(contractId, deliveryId, request);
    }

    @PostMapping("/{contractId}/close")
    public ContractView closeContract(@PathVariable Long contractId) {
        return contractService.closeContract(contractId);
    }
}
