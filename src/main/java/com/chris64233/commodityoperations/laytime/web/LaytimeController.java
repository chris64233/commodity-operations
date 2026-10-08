package com.chris64233.commodityoperations.laytime.web;

import com.chris64233.commodityoperations.laytime.dto.Requests.CreateVoyageRequest;
import com.chris64233.commodityoperations.laytime.dto.Requests.IngestEventRequest;
import com.chris64233.commodityoperations.laytime.dto.Requests.RecalculateRequest;
import com.chris64233.commodityoperations.laytime.dto.Responses.EventResponse;
import com.chris64233.commodityoperations.laytime.dto.Responses.SettlementResponse;
import com.chris64233.commodityoperations.laytime.dto.Responses.VoyageDetailResponse;
import com.chris64233.commodityoperations.laytime.dto.Responses.VoyageResponse;
import com.chris64233.commodityoperations.laytime.service.LaytimeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/laytime/voyages")
public class LaytimeController {

    private final LaytimeService service;

    public LaytimeController(LaytimeService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VoyageResponse createVoyage(@Valid @RequestBody CreateVoyageRequest req) {
        return service.createVoyage(req);
    }

    @PostMapping("/{voyageCode}/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventResponse ingestEvent(@PathVariable String voyageCode,
                                     @Valid @RequestBody IngestEventRequest req) {
        return service.ingestEvent(voyageCode, req);
    }

    /** 生成（或基于最新事件重新生成）结算版本。 */
    @PostMapping("/{voyageCode}/settlements/generate")
    public SettlementResponse generate(@PathVariable String voyageCode,
                                       @RequestBody(required = false) RecalculateRequest req) {
        return service.generateSettlement(voyageCode, req == null ? null : req.reason());
    }

    /** 重算入口：语义同 generate，已确认场景下产生差额调整版本。 */
    @PostMapping("/{voyageCode}/settlements/recalculate")
    public SettlementResponse recalculate(@PathVariable String voyageCode,
                                          @RequestBody(required = false) RecalculateRequest req) {
        return service.generateSettlement(voyageCode, req == null ? null : req.reason());
    }

    @PostMapping("/{voyageCode}/settlements/{settlementId}/confirm")
    public SettlementResponse confirm(@PathVariable String voyageCode,
                                      @PathVariable String settlementId) {
        return service.confirmSettlement(voyageCode, settlementId);
    }

    @PostMapping("/{voyageCode}/settlements/confirm-current")
    public SettlementResponse confirmCurrent(@PathVariable String voyageCode) {
        return service.confirmSettlement(voyageCode, null);
    }

    @GetMapping("/{voyageCode}")
    public VoyageDetailResponse detail(@PathVariable String voyageCode) {
        return service.getVoyageDetail(voyageCode);
    }
}
