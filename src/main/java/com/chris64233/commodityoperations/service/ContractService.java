package com.chris64233.commodityoperations.service;

import com.chris64233.commodityoperations.domain.Contract;
import com.chris64233.commodityoperations.domain.SettlementVersion;
import com.chris64233.commodityoperations.domain.SettlementVersionType;
import com.chris64233.commodityoperations.dto.*;
import com.chris64233.commodityoperations.repo.ContractRepository;
import com.chris64233.commodityoperations.repo.SettlementVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class ContractService {

    private final ContractRepository contractRepository;
    private final SettlementVersionRepository settlementVersionRepository;

    public ContractService(ContractRepository contractRepository,
                           SettlementVersionRepository settlementVersionRepository) {
        this.contractRepository = contractRepository;
        this.settlementVersionRepository = settlementVersionRepository;
    }

    @Transactional
    public ContractView register(RegisterContractRequest request) {
        if (!request.pricingWindowStart().isBefore(request.pricingWindowEnd())) {
            throw new BusinessRuleException("点价窗口必须满足 pricingWindowStart < pricingWindowEnd");
        }
        if (contractRepository.existsByContractNo(request.contractNo())) {
            throw new ConflictException("合同编号已存在: " + request.contractNo());
        }
        Contract contract = new Contract(
                request.contractNo(), request.commodity(), request.direction(),
                request.quantity(), request.currency(), request.provisionalPrice(),
                request.pricingWindowStart(), request.pricingWindowEnd(),
                request.allowedSources());
        contract = contractRepository.save(contract);

        SettlementVersion provisional = new SettlementVersion(
                contract, 1, SettlementVersionType.PROVISIONAL, null,
                contract.getProvisionalAmount(), BigDecimal.ZERO, Instant.now());
        settlementVersionRepository.save(provisional);
        contract.getSettlementVersions().add(provisional);

        return ContractView.from(contract, BigDecimal.ZERO, List.of(),
                List.of(SettlementVersionView.from(provisional)));
    }

    @Transactional(readOnly = true)
    public ContractView get(String contractNo) {
        Contract contract = contractRepository.findByContractNo(contractNo)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND,
                        "合同不存在: " + contractNo));
        return toView(contract);
    }

    @Transactional(readOnly = true)
    public ContractView getById(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND,
                        "合同不存在: id=" + id));
        return toView(contract);
    }

    private ContractView toView(Contract contract) {
        List<PricingSnapshotView> pricings = contract.getPricingOrders().stream()
                .sorted(Comparator.comparing(com.chris64233.commodityoperations.domain.PricingOrder::getId))
                .map(PricingSnapshotView::from)
                .toList();
        List<SettlementVersionView> versions = contract.getSettlementVersions().stream()
                .sorted(Comparator.comparingInt(
                        com.chris64233.commodityoperations.domain.SettlementVersion::getVersionNo))
                .map(SettlementVersionView::from)
                .toList();
        BigDecimal cumulative = versions.isEmpty()
                ? BigDecimal.ZERO.setScale(6)
                : versions.get(versions.size() - 1).cumulativeAdjustment();
        return ContractView.from(contract, cumulative, pricings, versions);
    }
}
