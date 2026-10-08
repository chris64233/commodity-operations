package com.chris64233.commodityoperations.contract;

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
import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    public record CreateContractRequest(@NotBlank String commodity,
                                        @NotNull @Positive BigDecimal baseQuantity,
                                        @NotNull BigDecimal shortTolerancePercent,
                                        @NotNull BigDecimal overTolerancePercent,
                                        @NotNull LocalDate deliveryDeadline,
                                        @NotBlank String unit) {
    }

    public record DeliveryRequest(@NotBlank String externalRef,
                                  @NotBlank String commodity,
                                  @NotBlank String unit,
                                  @NotNull @Positive BigDecimal quantity,
                                  @NotNull LocalDateTime deliveredAt) {
    }

    public record ReversalRequest(@NotBlank String externalRef,
                                  @NotNull LocalDateTime deliveredAt) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Contract createContract(@Valid @RequestBody CreateContractRequest request) {
        return contractService.createContract(request.commodity(), request.baseQuantity(),
                request.shortTolerancePercent(), request.overTolerancePercent(),
                request.deliveryDeadline(), request.unit());
    }

    @PostMapping("/{contractId}/deliveries")
    @ResponseStatus(HttpStatus.CREATED)
    public Delivery registerDelivery(@PathVariable Long contractId,
                                     @Valid @RequestBody DeliveryRequest request) {
        return contractService.registerDelivery(contractId, request.externalRef(), request.commodity(),
                request.unit(), request.quantity(), request.deliveredAt());
    }

    @PostMapping("/{contractId}/deliveries/{deliveryId}/reversals")
    @ResponseStatus(HttpStatus.CREATED)
    public Delivery reverseDelivery(@PathVariable Long contractId, @PathVariable Long deliveryId,
                                    @Valid @RequestBody ReversalRequest request) {
        return contractService.reverseDelivery(contractId, deliveryId, request.externalRef(),
                request.deliveredAt());
    }

    @PostMapping("/{contractId}/close")
    public ClosureRecord closeContract(@PathVariable Long contractId) {
        return contractService.closeContract(contractId);
    }

    @GetMapping("/{contractId}")
    public ContractService.ContractSummary getSummary(@PathVariable Long contractId) {
        return contractService.getSummary(contractId);
    }
}
