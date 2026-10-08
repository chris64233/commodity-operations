package com.chris64233.commodityoperations;

import com.chris64233.commodityoperations.dto.ContractRequests.CreateContractRequest;
import com.chris64233.commodityoperations.dto.ContractRequests.RegisterDeliveryRequest;
import com.chris64233.commodityoperations.dto.ContractRequests.ReverseDeliveryRequest;
import com.chris64233.commodityoperations.dto.ContractViews.ContractView;
import com.chris64233.commodityoperations.dto.ContractViews.DeliveryView;
import com.chris64233.commodityoperations.model.ContractStatus;
import com.chris64233.commodityoperations.model.DeliveryType;
import com.chris64233.commodityoperations.service.ContractException;
import com.chris64233.commodityoperations.service.ContractService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ContractServiceTest {

    @Autowired
    private ContractService contractService;

    private ContractView newContract() {
        return contractService.createContract(new CreateContractRequest(
                "电解铜", new BigDecimal("100"), new BigDecimal("5"), new BigDecimal("10"),
                LocalDate.of(2026, 12, 31), "吨"));
    }

    private RegisterDeliveryRequest delivery(String ref, String quantity) {
        return new RegisterDeliveryRequest(ref, "电解铜", "吨",
                new BigDecimal(quantity), LocalDate.of(2026, 10, 1));
    }

    @Test
    void registersDeliveriesUpToToleranceAndRejectsOverflow() {
        ContractView contract = newContract();
        assertThat(contract.minAcceptableQuantity()).isEqualByComparingTo("95");
        assertThat(contract.maxAcceptableQuantity()).isEqualByComparingTo("110");

        contractService.registerDelivery(contract.id(), delivery("D-1", "60"));
        contractService.registerDelivery(contract.id(), delivery("D-2", "50"));

        ContractView view = contractService.getContract(contract.id());
        assertThat(view.cumulativeQuantity()).isEqualByComparingTo("110");
        assertThat(view.remainingReceivableQuantity()).isEqualByComparingTo("0");
        assertThat(view.deliveries()).hasSize(2);
    }

    @Test
    void cumulativeDeliveriesCannotExceedMaxTolerance() {
        ContractView contract = newContract();
        contractService.registerDelivery(contract.id(), delivery("D-1", "60"));
        contractService.registerDelivery(contract.id(), delivery("D-2", "50"));

        assertThatThrownBy(() -> contractService.registerDelivery(contract.id(), delivery("D-3", "1")))
                .isInstanceOf(ContractException.class)
                .hasMessageContaining("溢装上限");

        ContractView view = contractService.getContract(contract.id());
        assertThat(view.cumulativeQuantity()).isEqualByComparingTo("110");
        assertThat(view.remainingReceivableQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsMismatchedCommodityOrUnit() {
        ContractView contract = newContract();
        assertThatThrownBy(() -> contractService.registerDelivery(contract.id(),
                new RegisterDeliveryRequest("D-1", "铝锭", "吨", new BigDecimal("10"),
                        LocalDate.of(2026, 10, 1))))
                .isInstanceOf(ContractException.class)
                .hasMessageContaining("不能混入同一合同");
        assertThatThrownBy(() -> contractService.registerDelivery(contract.id(),
                new RegisterDeliveryRequest("D-1", "电解铜", "千克", new BigDecimal("10"),
                        LocalDate.of(2026, 10, 1))))
                .isInstanceOf(ContractException.class)
                .hasMessageContaining("不能混入同一合同");
    }

    @Test
    void replayWithSameContentReturnsOriginalAndModifiedContentConflicts() {
        ContractView contract = newContract();
        DeliveryView first = contractService.registerDelivery(contract.id(), delivery("D-1", "60"));

        DeliveryView replay = contractService.registerDelivery(contract.id(), delivery("D-1", "60"));
        assertThat(replay.id()).isEqualTo(first.id());

        assertThatThrownBy(() -> contractService.registerDelivery(contract.id(), delivery("D-1", "61")))
                .isInstanceOf(ContractException.class)
                .hasMessageContaining("内容不一致");
        assertThatThrownBy(() -> contractService.registerDelivery(contract.id(),
                new RegisterDeliveryRequest("D-1", "电解铜", "吨", new BigDecimal("60"),
                        LocalDate.of(2026, 10, 2))))
                .isInstanceOf(ContractException.class)
                .hasMessageContaining("内容不一致");

        assertThat(contractService.getContract(contract.id()).cumulativeQuantity())
                .isEqualByComparingTo("60");
    }

    @Test
    void concurrentDeliveriesOnlySucceedWithinRemainingCapacity() throws Exception {
        ContractView contract = newContract();
        contractService.registerDelivery(contract.id(), delivery("D-0", "100"));

        List<String> results = runConcurrently(IntStream.rangeClosed(1, 4)
                .mapToObj(i -> (Callable<String>) () -> {
                    try {
                        contractService.registerDelivery(contract.id(), delivery("D-" + i, "10"));
                        return "OK";
                    } catch (ContractException e) {
                        return "REJECTED";
                    }
                }).toList());

        assertThat(results).containsExactlyInAnyOrder("OK", "REJECTED", "REJECTED", "REJECTED");
        assertThat(contractService.getContract(contract.id()).cumulativeQuantity())
                .isEqualByComparingTo("110");
    }

    @Test
    void closeRequiresMinimumQuantityAndBlocksLateDeliveries() {
        ContractView contract = newContract();
        contractService.registerDelivery(contract.id(), delivery("D-1", "60"));

        assertThatThrownBy(() -> contractService.closeContract(contract.id()))
                .isInstanceOf(ContractException.class)
                .hasMessageContaining("最低可接受数量");

        contractService.registerDelivery(contract.id(), delivery("D-2", "40"));
        ContractView closed = contractService.closeContract(contract.id());
        assertThat(closed.status()).isEqualTo(ContractStatus.CLOSED);
        assertThat(closed.closures()).hasSize(1);
        assertThat(closed.closures().get(0).cumulativeQuantity()).isEqualByComparingTo("100");
        assertThat(closed.remainingReceivableQuantity()).isEqualByComparingTo("0");

        assertThatThrownBy(() -> contractService.registerDelivery(contract.id(), delivery("D-3", "5")))
                .isInstanceOf(ContractException.class)
                .hasMessageContaining("迟到交货");
    }

    @Test
    void concurrentCloseAndDeliveryExactlyOneWins() throws Exception {
        for (int round = 0; round < 5; round++) {
            ContractView contract = newContract();
            contractService.registerDelivery(contract.id(), delivery("D-0", "96"));

            List<String> results = runConcurrently(List.of(
                    () -> {
                        try {
                            contractService.closeContract(contract.id());
                            return "CLOSED";
                        } catch (ContractException e) {
                            return "CLOSE_REJECTED";
                        }
                    },
                    () -> {
                        try {
                            contractService.registerDelivery(contract.id(), delivery("D-1", "10"));
                            return "DELIVERED";
                        } catch (ContractException e) {
                            return "DELIVERY_REJECTED";
                        }
                    }));

            ContractView view = contractService.getContract(contract.id());
            if (results.contains("CLOSED") && results.contains("DELIVERY_REJECTED")) {
                assertThat(view.status()).isEqualTo(ContractStatus.CLOSED);
                assertThat(view.cumulativeQuantity()).isEqualByComparingTo("96");
            } else {
                assertThat(results).containsExactlyInAnyOrder("DELIVERED", "CLOSED");
                assertThat(view.status()).isEqualTo(ContractStatus.CLOSED);
                assertThat(view.cumulativeQuantity()).isEqualByComparingTo("106");
            }
        }
    }

    @Test
    void reversalIsAppendOnlyAndMovesClosedContractToPendingReview() {
        ContractView contract = newContract();
        DeliveryView d1 = contractService.registerDelivery(contract.id(), delivery("D-1", "60"));
        contractService.registerDelivery(contract.id(), delivery("D-2", "40"));
        contractService.closeContract(contract.id());

        DeliveryView reversal = contractService.reverseDelivery(contract.id(), d1.id(),
                new ReverseDeliveryRequest("R-1", new BigDecimal("20"), LocalDate.of(2026, 10, 5)));
        assertThat(reversal.type()).isEqualTo(DeliveryType.REVERSAL);
        assertThat(reversal.quantity()).isEqualByComparingTo("-20");
        assertThat(reversal.reversalOfId()).isEqualTo(d1.id());

        ContractView view = contractService.getContract(contract.id());
        assertThat(view.status()).isEqualTo(ContractStatus.PENDING_REVIEW);
        assertThat(view.cumulativeQuantity()).isEqualByComparingTo("80");
        assertThat(view.deliveries()).hasSize(3);
        assertThat(view.deliveries().get(0).quantity()).isEqualByComparingTo("60");
        assertThat(view.closures()).hasSize(1);
        assertThat(view.closures().get(0).superseded()).isTrue();

        assertThatThrownBy(() -> contractService.reverseDelivery(contract.id(), d1.id(),
                new ReverseDeliveryRequest("R-2", new BigDecimal("41"), LocalDate.of(2026, 10, 6))))
                .isInstanceOf(ContractException.class)
                .hasMessageContaining("未冲销余额");
    }

    @Test
    void reversalWithinToleranceKeepsContractClosed() {
        ContractView contract = newContract();
        DeliveryView d1 = contractService.registerDelivery(contract.id(), delivery("D-1", "100"));
        contractService.closeContract(contract.id());

        contractService.reverseDelivery(contract.id(), d1.id(),
                new ReverseDeliveryRequest("R-1", new BigDecimal("3"), LocalDate.of(2026, 10, 5)));

        ContractView view = contractService.getContract(contract.id());
        assertThat(view.status()).isEqualTo(ContractStatus.CLOSED);
        assertThat(view.cumulativeQuantity()).isEqualByComparingTo("97");
        assertThat(view.closures().get(0).superseded()).isFalse();
    }

    private List<String> runConcurrently(List<Callable<String>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<String>> wrapped = tasks.stream().map(task -> (Callable<String>) () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            return task.call();
        }).toList();
        List<Future<String>> futures = wrapped.stream().map(executor::submit).toList();
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        List<String> results = futures.stream().map(f -> {
            try {
                return f.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).toList();
        executor.shutdown();
        return results;
    }
}
