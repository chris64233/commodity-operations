package com.chris64233.commodityoperations.service;

import com.chris64233.commodityoperations.domain.Contract;
import com.chris64233.commodityoperations.domain.ContractStatus;
import com.chris64233.commodityoperations.domain.SettlementVersion;
import com.chris64233.commodityoperations.domain.SettlementVersionType;
import com.chris64233.commodityoperations.dto.ContractView;
import com.chris64233.commodityoperations.repo.ContractRepository;
import com.chris64233.commodityoperations.repo.SettlementVersionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class SettlementService {

    private final ContractRepository contractRepository;
    private final SettlementVersionRepository settlementVersionRepository;
    private final ContractService contractService;

    public SettlementService(ContractRepository contractRepository,
                             SettlementVersionRepository settlementVersionRepository,
                             ContractService contractService) {
        this.contractRepository = contractRepository;
        this.settlementVersionRepository = settlementVersionRepository;
        this.contractService = contractService;
    }

    /**
     * 生成最终结算。仅当全部数量已点价时允许；最终结算与迟到点价由合同行锁串行化，
     * 已存在最终结算时重复请求幂等返回同一结果。
     */
    @Transactional
    public ContractView finalSettle(Long contractId) {
        Contract contract = contractRepository.findByIdForUpdate(contractId)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND,
                        "合同不存在: id=" + contractId));

        boolean alreadyFinal = contract.getSettlementVersions().stream()
                .anyMatch(v -> v.getType() == SettlementVersionType.FINAL);
        if (alreadyFinal || contract.getStatus() == ContractStatus.FINALLY_SETTLED) {
            return contractService.getById(contractId);
        }

        if (contract.unpricedQty().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessRuleException(
                    "仍有未点价数量，不能最终结算: 剩余="
                            + contract.unpricedQty().toPlainString());
        }

        BigDecimal cumulative = contract.getSettlementVersions().stream()
                .map(SettlementVersion::getCumulativeAdjustment)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        int nextVersionNo = contract.getSettlementVersions().size() + 1;
        SettlementVersion finalVersion = new SettlementVersion(
                contract, nextVersionNo, SettlementVersionType.FINAL, null,
                BigDecimal.ZERO.setScale(6), cumulative, Instant.now());
        settlementVersionRepository.save(finalVersion);
        contract.getSettlementVersions().add(finalVersion);
        contract.markFinallySettled();

        return contractService.getById(contractId);
    }
}
