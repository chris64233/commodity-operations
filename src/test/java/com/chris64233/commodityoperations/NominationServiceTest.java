package com.chris64233.commodityoperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.chris64233.commodityoperations.domain.DailyCapacity;
import com.chris64233.commodityoperations.domain.NominationStatus;
import com.chris64233.commodityoperations.domain.VesselNomination;
import com.chris64233.commodityoperations.dto.CapacityRequest;
import com.chris64233.commodityoperations.dto.ContractRequest;
import com.chris64233.commodityoperations.dto.ContractView;
import com.chris64233.commodityoperations.dto.HistoryView;
import com.chris64233.commodityoperations.dto.NominationRequest;
import com.chris64233.commodityoperations.dto.NominationView;
import com.chris64233.commodityoperations.dto.RescheduleRequest;
import com.chris64233.commodityoperations.repository.DailyCapacityRepository;
import com.chris64233.commodityoperations.repository.LoadingContractRepository;
import com.chris64233.commodityoperations.repository.NominationHistoryRepository;
import com.chris64233.commodityoperations.repository.VesselGuardRepository;
import com.chris64233.commodityoperations.repository.VesselNominationRepository;
import com.chris64233.commodityoperations.service.BusinessRuleException;
import com.chris64233.commodityoperations.service.NominationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class NominationServiceTest {

    private static final LocalDate D1 = LocalDate.of(2026, 10, 10);
    private static final LocalDate D2 = LocalDate.of(2026, 10, 11);
    private static final LocalDate D3 = LocalDate.of(2026, 10, 12);

    @Autowired
    private NominationService service;
    @Autowired
    private LoadingContractRepository contractRepository;
    @Autowired
    private DailyCapacityRepository capacityRepository;
    @Autowired
    private VesselNominationRepository nominationRepository;
    @Autowired
    private NominationHistoryRepository historyRepository;
    @Autowired
    private VesselGuardRepository vesselGuardRepository;

    @BeforeEach
    void cleanUp() {
        historyRepository.deleteAllInBatch();
        nominationRepository.deleteAllInBatch();
        vesselGuardRepository.deleteAllInBatch();
        capacityRepository.deleteAllInBatch();
        contractRepository.deleteAllInBatch();
    }

    @Test
    void rejectsQuantityExceedingRemainingContractQuantity() {
        registerContract("C-QTY", new BigDecimal("1000"));
        registerCapacity(D1, new BigDecimal("5000"));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.submit(nomination("C-QTY", "V-1", new BigDecimal("1200"), D1, null)));
        assertEquals("QUANTITY_EXCEEDED", ex.getCode());

        NominationView first = service.submit(
                nomination("C-QTY", "V-1", new BigDecimal("700"), D1, null));
        assertEquals(NominationStatus.PENDING, first.status());

        BusinessRuleException second = assertThrows(BusinessRuleException.class,
                () -> service.submit(
                        nomination("C-QTY", "V-2", new BigDecimal("400"), D1, null)));
        assertEquals("QUANTITY_EXCEEDED", second.getCode());

        ContractView view = service.getContract("C-QTY");
        assertEquals(0, new BigDecimal("700").compareTo(view.committedQuantity()));
        assertEquals(0, new BigDecimal("300").compareTo(view.remainingQuantity()));
    }

    @Test
    void rejectsNominationOutsideLoadingWindow() {
        registerContract("C-WIN", new BigDecimal("5000"));
        registerCapacity(LocalDate.of(2026, 10, 9), new BigDecimal("5000"));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.submit(nomination("C-WIN", "V-1",
                        new BigDecimal("100"), LocalDate.of(2026, 10, 9), null)));
        assertEquals("OUTSIDE_WINDOW", ex.getCode());
        assertEquals(0, nominationRepository.count());
    }

    @Test
    void rejectsSameVesselHoldingTwoActivePlans() {
        registerContract("C-A", new BigDecimal("5000"));
        registerCapacity(D1, new BigDecimal("5000"));

        service.submit(nomination("C-A", "V-DUP", new BigDecimal("100"), D1, null));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.submit(nomination("C-A", "V-DUP", new BigDecimal("100"), D2, null)));
        assertEquals("VESSEL_BUSY", ex.getCode());

        service.confirm(firstNominationId("V-DUP"));
        BusinessRuleException afterConfirm = assertThrows(BusinessRuleException.class,
                () -> service.submit(nomination("C-A", "V-DUP", new BigDecimal("100"), D2, null)));
        assertEquals("VESSEL_BUSY", afterConfirm.getCode());
    }

    @Test
    void withdrawOnlyAllowedBeforeConfirmation() {
        registerContract("C-W", new BigDecimal("1000"));
        registerCapacity(D1, new BigDecimal("1000"));

        Long id = service.submit(nomination("C-W", "V-W", new BigDecimal("300"), D1, null)).id();
        service.withdraw(id);

        VesselNomination withdrawn = nominationRepository.findById(id).orElseThrow();
        assertEquals(NominationStatus.WITHDRAWN, withdrawn.getStatus());
        assertFalse(withdrawn.isActiveFlag());

        BusinessRuleException confirmEx = assertThrows(BusinessRuleException.class,
                () -> service.confirm(id));
        assertEquals("NOMINATION_INACTIVE", confirmEx.getCode());

        service.submit(nomination("C-W", "V-W", new BigDecimal("400"), D1, null));

        Long id2 = service.submit(nomination("C-W", "V-X", new BigDecimal("200"), D1, null)).id();
        service.confirm(id2);
        BusinessRuleException withdrawEx = assertThrows(BusinessRuleException.class,
                () -> service.withdraw(id2));
        assertEquals("ALREADY_CONFIRMED", withdrawEx.getCode());
    }

    @Test
    void confirmAtomicallyReservesDailyCapacity() {
        registerContract("C-C", new BigDecimal("5000"));
        registerCapacity(D1, new BigDecimal("1000"));

        Long id = service.submit(nomination("C-C", "V-C", new BigDecimal("600"), D1, null)).id();
        service.confirm(id);

        DailyCapacity capacity = capacityRepository.findByDate(D1).orElseThrow();
        assertEquals(0, new BigDecimal("600").compareTo(capacity.getReservedCapacity()));

        Long second = service.submit(nomination("C-C", "V-C2", new BigDecimal("500"), D1, null)).id();
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.confirm(second));
        assertEquals("CAPACITY_EXCEEDED", ex.getCode());

        DailyCapacity after = capacityRepository.findByDate(D1).orElseThrow();
        assertEquals(0, new BigDecimal("600").compareTo(after.getReservedCapacity()));
        VesselNomination failed = nominationRepository.findById(second).orElseThrow();
        assertEquals(NominationStatus.PENDING, failed.getStatus());

        List<HistoryView> history = service.listHistory(id);
        assertTrue(history.stream().anyMatch(h -> "CONFIRMED".equals(h.action())));
    }

    @Test
    void failedRescheduleKeepsOriginalPlanAndCapacityIntact() {
        registerContract("C-R", new BigDecimal("5000"));
        registerCapacity(D1, new BigDecimal("1000"));
        registerCapacity(D2, new BigDecimal("1000"));

        Long id = service.submit(nomination("C-R", "V-R", new BigDecimal("800"), D1, null)).id();
        service.confirm(id);

        Long blockerId = service.submit(nomination("C-R", "V-B", new BigDecimal("700"), D2, null)).id();
        service.confirm(blockerId);

        LocalDateTime newEta = D2.atTime(14, 0);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.reschedule(id, new RescheduleRequest(newEta)));
        assertEquals("CAPACITY_EXCEEDED", ex.getCode());

        VesselNomination unchanged = nominationRepository.findById(id).orElseThrow();
        assertEquals(NominationStatus.CONFIRMED, unchanged.getStatus());
        assertEquals(D1, unchanged.getEstimatedArrival().toLocalDate());

        DailyCapacity oldCap = capacityRepository.findByDate(D1).orElseThrow();
        DailyCapacity newCap = capacityRepository.findByDate(D2).orElseThrow();
        assertEquals(0, new BigDecimal("800").compareTo(oldCap.getReservedCapacity()));
        assertEquals(0, new BigDecimal("700").compareTo(newCap.getReservedCapacity()));

        List<NominationView> plans = service.listVesselPlans("V-R");
        assertEquals(1, plans.size());
        assertEquals(D1, plans.get(0).estimatedArrival().toLocalDate());

        assertThrows(BusinessRuleException.class,
                () -> service.reschedule(id,
                        new RescheduleRequest(LocalDate.of(2026, 11, 1).atTime(8, 0))));
        assertEquals(D1, nominationRepository.findById(id).orElseThrow()
                .getEstimatedArrival().toLocalDate());
    }

    @Test
    void successfulRescheduleMovesCapacityFromOldDateToNewDate() {
        registerContract("C-S", new BigDecimal("5000"));
        registerCapacity(D1, new BigDecimal("1000"));
        registerCapacity(D2, new BigDecimal("1000"));

        Long id = service.submit(nomination("C-S", "V-S", new BigDecimal("600"), D1, null)).id();
        service.confirm(id);
        service.reschedule(id, new RescheduleRequest(D2.atTime(9, 30)));

        assertEquals(0, BigDecimal.ZERO.compareTo(
                capacityRepository.findByDate(D1).orElseThrow().getReservedCapacity()));
        assertEquals(0, new BigDecimal("600").compareTo(
                capacityRepository.findByDate(D2).orElseThrow().getReservedCapacity()));

        VesselNomination moved = nominationRepository.findById(id).orElseThrow();
        assertEquals(D2, moved.getEstimatedArrival().toLocalDate());
        assertEquals(NominationStatus.CONFIRMED, moved.getStatus());

        List<HistoryView> history = service.listHistory(id);
        assertTrue(history.stream().anyMatch(h -> "RESCHEDULED".equals(h.action())));
    }

    @Test
    void duplicateSubmitWithSameRequestIdIsIdempotent() {
        registerContract("C-I", new BigDecimal("5000"));
        registerCapacity(D1, new BigDecimal("5000"));

        NominationRequest request = nomination("C-I", "V-I", new BigDecimal("500"), D1, "req-001");
        NominationView first = service.submit(request);
        NominationView retry = service.submit(request);

        assertEquals(first.id(), retry.id());
        assertEquals(1, nominationRepository.count());

        service.confirm(first.id());
        NominationView repeatConfirm = service.confirm(first.id());
        assertEquals(NominationStatus.CONFIRMED, repeatConfirm.status());

        Long pendingId = service.submit(
                nomination("C-I", "V-I2", new BigDecimal("100"), D1, null)).id();
        service.withdraw(pendingId);
        assertEquals(NominationStatus.WITHDRAWN, service.withdraw(pendingId).status());
    }

    @Test
    void queriesExposeRemainingCapacityPlansAndHistory() {
        registerContract("C-Q", new BigDecimal("2000"));
        registerCapacity(D1, new BigDecimal("1000"));
        registerCapacity(D2, new BigDecimal("1000"));

        Long id = service.submit(nomination("C-Q", "V-Q", new BigDecimal("450"), D1, null)).id();
        service.confirm(id);

        assertEquals(2, service.listCapacities().size());
        assertNotNull(service.listVesselPlans("V-Q"));
        List<HistoryView> history = service.listHistory(id);
        assertEquals(2, history.size());
        assertEquals("SUBMITTED", history.get(0).action());
        assertEquals("CONFIRMED", history.get(1).action());
    }

    private void registerContract(String code, BigDecimal allowed) {
        service.registerContract(new ContractRequest(code, "矿主-" + code, allowed, D1, D3));
    }

    private void registerCapacity(LocalDate date, BigDecimal available) {
        service.registerCapacity(new CapacityRequest(date, available));
    }

    private NominationRequest nomination(String contractCode, String vesselCode,
                                         BigDecimal quantity, LocalDate date, String requestId) {
        return new NominationRequest(contractCode, vesselCode, "船-" + vesselCode,
                date.atTime(8, 0), quantity, requestId);
    }

    private Long firstNominationId(String vesselCode) {
        return nominationRepository.findAll().stream()
                .filter(n -> vesselCode.equals(n.getVesselCode()))
                .map(VesselNomination::getId)
                .findFirst()
                .orElseThrow();
    }
}
