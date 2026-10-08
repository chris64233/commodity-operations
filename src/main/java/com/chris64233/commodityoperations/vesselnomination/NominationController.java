package com.chris64233.commodityoperations.vesselnomination;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nominations")
public class NominationController {

    private final NominationService service;

    public NominationController(NominationService service) {
        this.service = service;
    }

    public record ContractRequest(@NotBlank String contractNumber,
                                  @Positive long allowedQuantity,
                                  @NotNull LocalDate windowStart,
                                  @NotNull LocalDate windowEnd) {
    }

    public record CapacityRequest(@NotNull LocalDate date, @Positive long totalCapacity) {
    }

    public record SubmitRequest(@NotNull Long contractId,
                                @NotBlank String vesselName,
                                @NotNull LocalDate eta,
                                @Positive long plannedQuantity) {
    }

    public record RescheduleRequest(@NotNull LocalDate newDate) {
    }

    @PostMapping("/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    public LoadingContract registerContract(@Valid @RequestBody ContractRequest request) {
        return service.registerContract(request.contractNumber(), request.allowedQuantity(),
                request.windowStart(), request.windowEnd());
    }

    @PostMapping("/capacities")
    @ResponseStatus(HttpStatus.CREATED)
    public TerminalDailyCapacity registerCapacity(@Valid @RequestBody CapacityRequest request) {
        return service.registerDailyCapacity(request.date(), request.totalCapacity());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VesselNomination submit(@Valid @RequestBody SubmitRequest request) {
        return service.submit(request.contractId(), request.vesselName(), request.eta(), request.plannedQuantity());
    }

    @PostMapping("/{id}/confirm")
    public VesselNomination confirm(@PathVariable Long id) {
        return service.confirm(id);
    }

    @PostMapping("/{id}/withdraw")
    public VesselNomination withdraw(@PathVariable Long id) {
        return service.withdraw(id);
    }

    @PostMapping("/{id}/reschedule")
    public VesselNomination reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleRequest request) {
        return service.reschedule(id, request.newDate());
    }

    @GetMapping("/contracts/{id}/remaining")
    public Map<String, Long> remaining(@PathVariable Long id) {
        return Map.of("contractId", id, "remainingQuantity", service.remainingQuantity(id));
    }

    @GetMapping("/capacities")
    public TerminalDailyCapacity dailyCapacity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.dailyCapacity(date);
    }

    @GetMapping("/vessels/{vesselName}/current")
    public VesselNomination currentPlan(@PathVariable String vesselName) {
        return service.currentPlan(vesselName);
    }

    @GetMapping("/{id}/events")
    public List<NominationEvent> history(@PathVariable Long id) {
        return service.history(id);
    }

    @ExceptionHandler(NominationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleNominationException(NominationException ex) {
        return Map.of("error", ex.getMessage());
    }
}
