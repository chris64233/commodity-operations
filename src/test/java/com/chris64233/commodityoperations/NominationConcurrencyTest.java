package com.chris64233.commodityoperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.chris64233.commodityoperations.domain.DailyCapacity;
import com.chris64233.commodityoperations.domain.NominationStatus;
import com.chris64233.commodityoperations.dto.CapacityRequest;
import com.chris64233.commodityoperations.dto.ContractRequest;
import com.chris64233.commodityoperations.dto.NominationRequest;
import com.chris64233.commodityoperations.repository.DailyCapacityRepository;
import com.chris64233.commodityoperations.repository.LoadingContractRepository;
import com.chris64233.commodityoperations.repository.NominationHistoryRepository;
import com.chris64233.commodityoperations.repository.VesselGuardRepository;
import com.chris64233.commodityoperations.repository.VesselNominationRepository;
import com.chris64233.commodityoperations.service.BusinessRuleException;
import com.chris64233.commodityoperations.service.NominationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class NominationConcurrencyTest {

    private static final LocalDate D1 = LocalDate.of(2026, 10, 10);
    private static final LocalDate D2 = LocalDate.of(2026, 10, 11);

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

    private ExecutorService executor;

    @BeforeEach
    void cleanUp() {
        historyRepository.deleteAllInBatch();
        nominationRepository.deleteAllInBatch();
        vesselGuardRepository.deleteAllInBatch();
        capacityRepository.deleteAllInBatch();
        contractRepository.deleteAllInBatch();
        executor = Executors.newFixedThreadPool(3);
    }

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void concurrentConfirmationsNeverExceedDailyCapacity() throws Exception {
        service.registerContract(new ContractRequest(
                "C-CON", "矿主", new BigDecimal("10000"), D1, D2));
        service.registerCapacity(new CapacityRequest(D1, new BigDecimal("800")));

        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            ids.add(service.submit(new NominationRequest(
                    "C-CON", "VC-" + i, "船" + i,
                    D1.atTime(8, 0), new BigDecimal("500"), null)).id());
        }

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger confirmed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (Long id : ids) {
            futures.add(executor.submit(() -> {
                start.await();
                try {
                    service.confirm(id);
                    confirmed.incrementAndGet();
                } catch (BusinessRuleException ex) {
                    if ("CAPACITY_EXCEEDED".equals(ex.getCode())) {
                        rejected.incrementAndGet();
                    } else {
                        throw ex;
                    }
                }
                return null;
            }));
        }

        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        assertEquals(1, confirmed.get(), "只能有一个提报抢得能力");
        assertEquals(2, rejected.get(), "其余并发确认必须被拒绝");

        DailyCapacity capacity = capacityRepository.findByDate(D1).orElseThrow();
        assertEquals(0, new BigDecimal("500").compareTo(capacity.getReservedCapacity()),
                "并发确认后预留量绝不能超过日能力 800");
        assertTrue(capacity.getReservedCapacity().compareTo(capacity.getAvailableCapacity()) <= 0);

        long confirmedCount = nominationRepository.findAll().stream()
                .filter(n -> n.getStatus() == NominationStatus.CONFIRMED)
                .count();
        assertEquals(1, confirmedCount);
    }

    @Test
    void concurrentSubmissionsForSameVesselAllowOnlyOneActivePlan() throws Exception {
        service.registerContract(new ContractRequest(
                "C-VES", "矿主", new BigDecimal("10000"), D1, D2));
        service.registerCapacity(new CapacityRequest(D1, new BigDecimal("10000")));

        int attempts = 4;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            futures.add(executor.submit(() -> {
                start.await();
                try {
                    service.submit(new NominationRequest(
                            "C-VES", "VSAME", "同船",
                            D1.atTime(9, 0), new BigDecimal("100"), null));
                    accepted.incrementAndGet();
                } catch (BusinessRuleException ex) {
                    if ("VESSEL_BUSY".equals(ex.getCode())) {
                        rejected.incrementAndGet();
                    } else {
                        throw new RuntimeException(ex);
                    }
                } catch (RuntimeException ex) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }

        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }

        assertEquals(1, accepted.get(), "同一船舶并发提报只能成功一个");
        assertEquals(attempts - 1, rejected.get());
        assertEquals(1, nominationRepository.findByVesselCodeAndActiveFlagTrueOrderByIdAsc("VSAME")
                .size());
    }

    @Test
    void duplicateConcurrentRequestsWithSameRequestIdCreateOnlyOneNomination() throws Exception {
        service.registerContract(new ContractRequest(
                "C-IDEM", "矿主", new BigDecimal("10000"), D1, D2));
        service.registerCapacity(new CapacityRequest(D1, new BigDecimal("10000")));

        int attempts = 3;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            futures.add(executor.submit(() -> {
                start.await();
                return service.submit(new NominationRequest(
                        "C-IDEM", "VIDEM", "幂等船",
                        D1.atTime(10, 0), new BigDecimal("200"), "idem-req-9")).id();
            }));
        }

        start.countDown();
        Long firstId = null;
        for (Future<Long> future : futures) {
            Long id = future.get(30, TimeUnit.SECONDS);
            if (firstId == null) {
                firstId = id;
            } else {
                assertEquals(firstId, id, "重复请求必须返回同一提报");
            }
        }

        assertEquals(1, nominationRepository.count(), "重复请求不得产生多条提报");
        assertEquals(firstId,
                nominationRepository.findByRequestId("idem-req-9").orElseThrow().getId());
    }
}
