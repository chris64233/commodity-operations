package com.chris64233.commodityoperations.contract;

import com.chris64233.commodityoperations.contract.ContractExceptions.ConflictException;
import com.chris64233.commodityoperations.contract.ContractExceptions.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ContractServiceTest {

    @Autowired
    private ContractService contractService;

    private Contract newContract() {
        return contractService.createContract("电解铜", new BigDecimal("100"),
                new BigDecimal("5"), new BigDecimal("10"),
                LocalDate.now().plusDays(30), "吨");
    }

    @Test
    void registersDeliveriesWithinToleranceAndRejectsOverflow() {
        Contract contract = newContract();
        contractService.registerDelivery(contract.getId(), "REF-1", "电解铜", "吨",
                new BigDecimal("60"), LocalDateTime.now());
        contractService.registerDelivery(contract.getId(), "REF-2", "电解铜", "吨",
                new BigDecimal("50"), LocalDateTime.now());

        assertThatThrownBy(() -> contractService.registerDelivery(contract.getId(), "REF-3",
                "电解铜", "吨", new BigDecimal("0.0001"), LocalDateTime.now()))
                .isInstanceOf(ConflictException.class);

        var summary = contractService.getSummary(contract.getId());
        assertThat(summary.cumulativeQuantity()).isEqualByComparingTo("110");
        assertThat(summary.minAcceptableQuantity()).isEqualByComparingTo("95");
        assertThat(summary.maxAcceptableQuantity()).isEqualByComparingTo("110");
        assertThat(summary.remainingReceivableQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsMismatchedCommodityOrUnit() {
        Contract contract = newContract();
        assertThatThrownBy(() -> contractService.registerDelivery(contract.getId(), "REF-1",
                "铝锭", "吨", new BigDecimal("10"), LocalDateTime.now()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> contractService.registerDelivery(contract.getId(), "REF-1",
                "电解铜", "千克", new BigDecimal("10"), LocalDateTime.now()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void replayWithSameContentReturnsOriginalAndDifferentContentConflicts() {
        Contract contract = newContract();
        LocalDateTime deliveredAt = LocalDateTime.now();
        Delivery first = contractService.registerDelivery(contract.getId(), "REF-1", "电解铜", "吨",
                new BigDecimal("10"), deliveredAt);

        Delivery replay = contractService.registerDelivery(contract.getId(), "REF-1", "电解铜", "吨",
                new BigDecimal("10"), deliveredAt);
        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(contractService.getSummary(contract.getId()).deliveries()).hasSize(1);

        assertThatThrownBy(() -> contractService.registerDelivery(contract.getId(), "REF-1",
                "电解铜", "吨", new BigDecimal("20"), deliveredAt))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> contractService.registerDelivery(contract.getId(), "REF-1",
                "电解铜", "吨", new BigDecimal("10"), deliveredAt.plusHours(1)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void concurrentDeliveriesOnlySucceedWithinRemainingCapacity() throws InterruptedException {
        Contract contract = newContract();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        for (int i = 0; i < threads; i++) {
            int index = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    contractService.registerDelivery(contract.getId(), "REF-" + index, "电解铜", "吨",
                            new BigDecimal("20"), LocalDateTime.now());
                    succeeded.incrementAndGet();
                } catch (ConflictException e) {
                    failures.add(e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(succeeded.get()).isEqualTo(5);
        assertThat(failures).hasSize(3);
        assertThat(contractService.getSummary(contract.getId()).cumulativeQuantity())
                .isEqualByComparingTo("100");
    }

    @Test
    void closeRequiresMinimumQuantityAndRejectsLateDeliveries() {
        Contract contract = newContract();
        contractService.registerDelivery(contract.getId(), "REF-1", "电解铜", "吨",
                new BigDecimal("50"), LocalDateTime.now());
        assertThatThrownBy(() -> contractService.closeContract(contract.getId()))
                .isInstanceOf(ConflictException.class);

        contractService.registerDelivery(contract.getId(), "REF-2", "电解铜", "吨",
                new BigDecimal("45"), LocalDateTime.now());
        ClosureRecord closure = contractService.closeContract(contract.getId());
        assertThat(closure.getCumulativeQuantity()).isEqualByComparingTo("95");

        ClosureRecord again = contractService.closeContract(contract.getId());
        assertThat(again.getId()).isEqualTo(closure.getId());

        assertThatThrownBy(() -> contractService.registerDelivery(contract.getId(), "REF-3",
                "电解铜", "吨", new BigDecimal("1"), LocalDateTime.now()))
                .isInstanceOf(ConflictException.class);
        assertThat(contractService.getSummary(contract.getId()).contract().getStatus())
                .isEqualTo(ContractStatus.CLOSED);
    }

    @Test
    void concurrentCloseAndDeliverySettleExactlyOneOrdering() throws InterruptedException {
        Contract contract = newContract();
        contractService.registerDelivery(contract.getId(), "REF-1", "电解铜", "吨",
                new BigDecimal("94"), LocalDateTime.now());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Object> results = new CopyOnWriteArrayList<>();
        Runnable deliveryTask = () -> {
            ready.countDown();
            try {
                go.await();
                contractService.registerDelivery(contract.getId(), "REF-2", "电解铜", "吨",
                        new BigDecimal("10"), LocalDateTime.now());
                results.add("DELIVERY_OK");
            } catch (ConflictException e) {
                results.add("DELIVERY_CONFLICT");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        Runnable closeTask = () -> {
            ready.countDown();
            try {
                go.await();
                contractService.closeContract(contract.getId());
                results.add("CLOSE_OK");
            } catch (ConflictException e) {
                results.add("CLOSE_CONFLICT");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        pool.submit(deliveryTask);
        pool.submit(closeTask);
        ready.await();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        var summary = contractService.getSummary(contract.getId());
        if (results.contains("DELIVERY_OK")) {
            assertThat(summary.cumulativeQuantity()).isEqualByComparingTo("104");
            if (results.contains("CLOSE_OK")) {
                assertThat(summary.closureHistory().get(0).getCumulativeQuantity())
                        .isEqualByComparingTo("104");
            } else {
                assertThat(results).containsExactlyInAnyOrder("DELIVERY_OK", "CLOSE_CONFLICT");
                assertThat(summary.contract().getStatus()).isEqualTo(ContractStatus.OPEN);
            }
        } else {
            assertThat(results).containsExactlyInAnyOrder("DELIVERY_CONFLICT", "CLOSE_OK");
            assertThat(summary.cumulativeQuantity()).isEqualByComparingTo("94");
            assertThat(summary.contract().getStatus()).isEqualTo(ContractStatus.CLOSED);
        }
    }

    @Test
    void reversalBelowMinimumMovesClosedContractToPendingReviewKeepingHistory() {
        Contract contract = newContract();
        Delivery delivery = contractService.registerDelivery(contract.getId(), "REF-1",
                "电解铜", "吨", new BigDecimal("100"), LocalDateTime.now());
        contractService.closeContract(contract.getId());

        Delivery reversal = contractService.reverseDelivery(contract.getId(), delivery.getId(),
                "REV-1", LocalDateTime.now());
        assertThat(reversal.getType()).isEqualTo(DeliveryType.REVERSAL);
        assertThat(reversal.getQuantity()).isEqualByComparingTo("-100");

        var summary = contractService.getSummary(contract.getId());
        assertThat(summary.contract().getStatus()).isEqualTo(ContractStatus.PENDING_REVIEW);
        assertThat(summary.cumulativeQuantity()).isEqualByComparingTo("0");
        assertThat(summary.closureHistory()).hasSize(1);
        assertThat(summary.closureHistory().get(0).getState()).isEqualTo(ClosureRecord.State.REOPENED);
        assertThat(summary.closureHistory().get(0).getCumulativeQuantity()).isEqualByComparingTo("100");

        assertThatThrownBy(() -> contractService.closeContract(contract.getId()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> contractService.registerDelivery(contract.getId(), "REF-2",
                "电解铜", "吨", new BigDecimal("10"), LocalDateTime.now()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> contractService.reverseDelivery(contract.getId(), delivery.getId(),
                "REV-2", LocalDateTime.now()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void reversalReplayReturnsOriginalAndOriginalDeliveryIsKept() {
        Contract contract = newContract();
        Delivery delivery = contractService.registerDelivery(contract.getId(), "REF-1",
                "电解铜", "吨", new BigDecimal("50"), LocalDateTime.now());
        LocalDateTime reversedAt = LocalDateTime.now();
        Delivery reversal = contractService.reverseDelivery(contract.getId(), delivery.getId(),
                "REV-1", reversedAt);

        Delivery replay = contractService.reverseDelivery(contract.getId(), delivery.getId(),
                "REV-1", reversedAt);
        assertThat(replay.getId()).isEqualTo(reversal.getId());

        var summary = contractService.getSummary(contract.getId());
        assertThat(summary.deliveries()).hasSize(2);
        assertThat(summary.deliveries().get(0).getType()).isEqualTo(DeliveryType.DELIVERY);
        assertThat(summary.cumulativeQuantity()).isEqualByComparingTo("0");
    }
}
