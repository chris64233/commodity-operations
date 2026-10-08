package com.chris64233.commodityoperations.web;

import java.util.List;

import com.chris64233.commodityoperations.dto.CapacityRequest;
import com.chris64233.commodityoperations.dto.CapacityView;
import com.chris64233.commodityoperations.dto.ContractRequest;
import com.chris64233.commodityoperations.dto.ContractView;
import com.chris64233.commodityoperations.dto.HistoryView;
import com.chris64233.commodityoperations.dto.NominationRequest;
import com.chris64233.commodityoperations.dto.NominationView;
import com.chris64233.commodityoperations.dto.RescheduleRequest;
import com.chris64233.commodityoperations.service.NominationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class NominationController {

    private final NominationService nominationService;

    public NominationController(NominationService nominationService) {
        this.nominationService = nominationService;
    }

    @PostMapping("/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractView registerContract(@Valid @RequestBody ContractRequest request) {
        return nominationService.registerContract(request);
    }

    @GetMapping("/contracts/{contractCode}")
    public ContractView getContract(@PathVariable String contractCode) {
        return nominationService.getContract(contractCode);
    }

    @PostMapping("/capacities")
    @ResponseStatus(HttpStatus.CREATED)
    public CapacityView registerCapacity(@Valid @RequestBody CapacityRequest request) {
        return nominationService.registerCapacity(request);
    }

    @GetMapping("/capacities")
    public List<CapacityView> listCapacities() {
        return nominationService.listCapacities();
    }

    @PostMapping("/nominations")
    @ResponseStatus(HttpStatus.CREATED)
    public NominationView submit(@Valid @RequestBody NominationRequest request) {
        return nominationService.submit(request);
    }

    @PostMapping("/nominations/{id}/confirm")
    public NominationView confirm(@PathVariable Long id) {
        return nominationService.confirm(id);
    }

    @PostMapping("/nominations/{id}/withdraw")
    public NominationView withdraw(@PathVariable Long id) {
        return nominationService.withdraw(id);
    }

    @PostMapping("/nominations/{id}/reschedule")
    public NominationView reschedule(@PathVariable Long id,
                                     @Valid @RequestBody RescheduleRequest request) {
        return nominationService.reschedule(id, request);
    }

    @GetMapping("/nominations/{id}/history")
    public List<HistoryView> history(@PathVariable Long id) {
        return nominationService.listHistory(id);
    }

    @GetMapping("/vessels/{vesselCode}/plans")
    public List<NominationView> vesselPlans(@PathVariable String vesselCode) {
        return nominationService.listVesselPlans(vesselCode);
    }

    @GetMapping("/vessels/plans")
    public List<NominationView> vesselPlansByParam(@RequestParam String vesselCode) {
        return nominationService.listVesselPlans(vesselCode);
    }
}
