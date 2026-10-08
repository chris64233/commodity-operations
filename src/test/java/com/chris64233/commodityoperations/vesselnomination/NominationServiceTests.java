package com.chris64233.commodityoperations.vesselnomination;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class NominationServiceTests {

    private static final LocalDate DAY1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate DAY2 = LocalDate.of(2026, 11, 2);
    private static final LocalDate OUTSIDE = LocalDate.of(2026, 12, 1);

    @Autowired
    private NominationService service;

    @Autowired
    private LoadingContractRepository contractRepository;

    @Autowired
    private TerminalDailyCapacityRepository capacityRepository;

    @Autowired
    private VesselNominationRepository nominationRepository;

    @Autowired
    private NominationEventRepository eventRepository;

    private LoadingContract contract;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        nominationRepository.deleteAll();
        capacityRepository.deleteAll();
        contractRepository.deleteAll();
        contract = service.registerContract("CT-001", 10_000, DAY1, DAY2);
    }

    @Test
    void submitRejectsQuantityExceedingRemainingAndEtaOutsideWindow() {
        service.submit(contract.getId(), "VESSEL-A", DAY1, 8_000);

        assertThatThrownBy(() -> service.submit(contract.getId(), "VESSEL-B", DAY1, 3_000))
                .isInstanceOf(NominationException.class)
                .hasMessageContaining("超出合同剩余数量");
        assertThatThrownBy(() -> service.submit(contract.getId(), "VESSEL-B", OUTSIDE, 1_000))
                .isInstanceOf(NominationException.class)
                .hasMessageContaining("装期窗口");
        assertThat(service.remainingQuantity(contract.getId())).isEqualTo(2_000);
    }

    @Test
    void confirmReservesCapacityAndWithdrawReleasesQuotaBeforeConfirmation() {
        service.registerDailyCapacity(DAY1, 5_000);
        VesselNomination confirmed = service.submit(contract.getId(), "VESSEL-A", DAY1, 4_000);
        service.confirm(confirmed.getId());

        assertThat(service.dailyCapacity(DAY1).getReservedCapacity()).isEqualTo(4_000);
        assertThat(service.currentPlan("VESSEL-A").getReservedDate()).isEqualTo(DAY1);

        VesselNomination withdrawn = service.submit(contract.getId(), "VESSEL-B", DAY1, 1_000);
        service.withdraw(withdrawn.getId());

        assertThat(service.remainingQuantity(contract.getId())).isEqualTo(6_000);
        assertThatThrownBy(() -> service.confirm(withdrawn.getId()))
                .isInstanceOf(NominationException.class);
        assertThatThrownBy(() -> service.withdraw(confirmed.getId()))
                .isInstanceOf(NominationException.class)
                .hasMessageContaining("不能撤回");
    }

    @Test
    void concurrentConfirmationsNeverExceedDailyCapacity() throws Exception {
        service.registerDailyCapacity(DAY1, 5_000);
        VesselNomination first = service.submit(contract.getId(), "VESSEL-A", DAY1, 4_000);
        VesselNomination second = service.submit(contract.getId(), "VESSEL-B", DAY1, 4_000);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Runnable task1 = confirmTask(first.getId(), ready, start);
        Runnable task2 = confirmTask(second.getId(), ready, start);
        Future<?> f1 = executor.submit(task1);
        Future<?> f2 = executor.submit(task2);
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        f1.get(30, TimeUnit.SECONDS);
        f2.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        TerminalDailyCapacity capacity = service.dailyCapacity(DAY1);
        assertThat(capacity.getReservedCapacity()).isLessThanOrEqualTo(capacity.getTotalCapacity());
        assertThat(capacity.getReservedCapacity()).isEqualTo(4_000);

        long confirmedCount = List.of(first.getId(), second.getId()).stream()
                .map(id -> nominationRepository.findById(id).orElseThrow().getStatus())
                .filter(status -> status == NominationStatus.CONFIRMED)
                .count();
        assertThat(confirmedCount).isEqualTo(1);
    }

    private Runnable confirmTask(Long nominationId, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            try {
                start.await(10, TimeUnit.SECONDS);
                service.confirm(nominationId);
            } catch (NominationException expected) {
                // 竞争失败的一方：能力已被占满
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        };
    }

    @Test
    void sameVesselCannotHoldTwoActivePlans() {
        service.registerDailyCapacity(DAY1, 10_000);
        service.registerDailyCapacity(DAY2, 10_000);
        VesselNomination first = service.submit(contract.getId(), "VESSEL-A", DAY1, 3_000);
        VesselNomination second = service.submit(contract.getId(), "VESSEL-A", DAY2, 3_000);
        service.confirm(first.getId());

        assertThatThrownBy(() -> service.confirm(second.getId()))
                .isInstanceOf(NominationException.class)
                .hasMessageContaining("已存在有效计划");
        assertThat(service.dailyCapacity(DAY2).getReservedCapacity()).isZero();
    }

    @Test
    void failedConfirmLeavesNoResidualCapacityDeduction() {
        service.registerDailyCapacity(DAY1, 2_000);
        VesselNomination nomination = service.submit(contract.getId(), "VESSEL-A", DAY1, 5_000);

        assertThatThrownBy(() -> service.confirm(nomination.getId()))
                .isInstanceOf(NominationException.class)
                .hasMessageContaining("可用能力不足");
        assertThat(service.dailyCapacity(DAY1).getReservedCapacity()).isZero();
        assertThat(nominationRepository.findById(nomination.getId()).orElseThrow().getStatus())
                .isEqualTo(NominationStatus.PENDING);
    }

    @Test
    void rescheduleFailureKeepsOriginalPlanUntouched() {
        service.registerDailyCapacity(DAY1, 5_000);
        service.registerDailyCapacity(DAY2, 1_000);
        VesselNomination nomination = service.submit(contract.getId(), "VESSEL-A", DAY1, 4_000);
        service.confirm(nomination.getId());

        assertThatThrownBy(() -> service.reschedule(nomination.getId(), DAY2))
                .isInstanceOf(NominationException.class)
                .hasMessageContaining("可用能力不足");

        VesselNomination current = service.currentPlan("VESSEL-A");
        assertThat(current.getReservedDate()).isEqualTo(DAY1);
        assertThat(service.dailyCapacity(DAY1).getReservedCapacity()).isEqualTo(4_000);
        assertThat(service.dailyCapacity(DAY2).getReservedCapacity()).isZero();
    }

    @Test
    void rescheduleMovesCapacityAtomicallyToNewDate() {
        service.registerDailyCapacity(DAY1, 5_000);
        service.registerDailyCapacity(DAY2, 5_000);
        VesselNomination nomination = service.submit(contract.getId(), "VESSEL-A", DAY1, 4_000);
        service.confirm(nomination.getId());

        service.reschedule(nomination.getId(), DAY2);

        assertThat(service.currentPlan("VESSEL-A").getReservedDate()).isEqualTo(DAY2);
        assertThat(service.dailyCapacity(DAY1).getReservedCapacity()).isZero();
        assertThat(service.dailyCapacity(DAY2).getReservedCapacity()).isEqualTo(4_000);
        assertThat(service.history(nomination.getId()))
                .extracting(NominationEvent::getEventType)
                .containsExactly("SUBMITTED", "CONFIRMED", "RESCHEDULED");
    }

    @Test
    void duplicateConfirmAndWithdrawAreIdempotent() {
        service.registerDailyCapacity(DAY1, 5_000);
        VesselNomination nomination = service.submit(contract.getId(), "VESSEL-A", DAY1, 4_000);

        service.confirm(nomination.getId());
        service.confirm(nomination.getId());
        service.reschedule(nomination.getId(), DAY1);

        assertThat(service.dailyCapacity(DAY1).getReservedCapacity()).isEqualTo(4_000);
        assertThat(service.history(nomination.getId()))
                .extracting(NominationEvent::getEventType)
                .containsExactly("SUBMITTED", "CONFIRMED");

        VesselNomination other = service.submit(contract.getId(), "VESSEL-B", DAY1, 1_000);
        service.withdraw(other.getId());
        service.withdraw(other.getId());
        assertThat(nominationRepository.findById(other.getId()).orElseThrow().getStatus())
                .isEqualTo(NominationStatus.WITHDRAWN);
    }
}
