package com.chris64233.commodityoperations.laytime.web;

import com.chris64233.commodityoperations.laytime.domain.Voyage;
import com.chris64233.commodityoperations.laytime.service.LaytimeService;
import com.chris64233.commodityoperations.laytime.service.SnapshotDtos.SettlementView;
import com.chris64233.commodityoperations.laytime.service.SnapshotDtos.VoyageDetailView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/laytime/voyages")
public class LaytimeController {

    private final LaytimeService laytimeService;

    public LaytimeController(LaytimeService laytimeService) {
        this.laytimeService = laytimeService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> registerVoyage(@Valid @RequestBody VoyageRequest request) {
        Voyage voyage = laytimeService.registerVoyage(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("voyageNo", voyage.getVoyageNo(), "eventVersion", voyage.getEventVersion()));
    }

    @PostMapping("/{voyageNo}/events")
    public Map<String, Object> ingestEvent(@PathVariable String voyageNo,
                                           @Valid @RequestBody EventRequest request) {
        boolean accepted = laytimeService.ingestEvent(voyageNo, request);
        return Map.of(
                "externalEventNo", request.externalEventNo(),
                "replayed", !accepted,
                "message", accepted ? "事件已接收" : "同号同内容事件重放，结果不变");
    }

    @PostMapping("/{voyageNo}/settlements")
    public SettlementView generateSettlement(@PathVariable String voyageNo,
                                             @RequestBody(required = false) RecalculateRequest body) {
        String reason = body == null ? null : body.reason();
        return laytimeService.generateSettlement(voyageNo, reason);
    }

    @PostMapping("/{voyageNo}/settlements/confirm")
    public SettlementView confirmSettlement(@PathVariable String voyageNo,
                                            @RequestBody(required = false) ConfirmRequest body) {
        Integer expectedVersionNo = body == null ? null : body.versionNo();
        return laytimeService.confirmSettlement(voyageNo, expectedVersionNo);
    }

    @GetMapping("/{voyageNo}")
    public VoyageDetailView getVoyage(@PathVariable String voyageNo) {
        return laytimeService.getVoyageDetail(voyageNo);
    }
}
