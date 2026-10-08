package com.chris64233.commodityoperations.service;

import com.chris64233.commodityoperations.domain.*;
import com.chris64233.commodityoperations.dto.ContractView;
import com.chris64233.commodityoperations.dto.SubmitPricingRequest;
import com.chris64233.commodityoperations.repo.ContractRepository;
import com.chris64233.commodityoperations.repo.PricingOrderRepository;
import com.chris64233.commodityoperations.repo.SettlementVersionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PricingService {

    private final ContractRepository contractRepository;
    private final PricingOrderRepository pricingOrderRepository;
    private final SettlementVersionRepository settlementVersionRepository;
    private final MarketPriceService marketPriceService;
    private final ContractService contractService;

    public PricingService(ContractRepository contractRepository,
                          PricingOrderRepository pricingOrderRepository,
                          SettlementVersionRepository settlementVersionRepository,
                          MarketPriceService marketPriceService,
                          ContractService contractService) {
        this.contractRepository = contractRepository;
        this.pricingOrderRepository = pricingOrderRepository;
        this.settlementVersionRepository = settlementVersionRepository;
        this.marketPriceService = marketPriceService;
        this.contractService = contractService;
    }

    /**
     * 提交点价。合同行级悲观锁保证并发请求只消耗仍未点价的数量；
     * 外部点价号相同且内容相同则幂等返回原结果，内容不同则 409。
     */
    @Transactional
    public ContractView submitPricing(Long contractId, SubmitPricingRequest request) {
        Contract contract = contractRepository.findByIdForUpdate(contractId)
                .orElseThrow(() -> new BusinessRuleException(HttpStatus.NOT_FOUND,
                        "合同不存在: id=" + contractId));

        var existing = pricingOrderRepository.findByExternalPricingNo(
                request.externalPricingNo());
        if (existing.isPresent()) {
            PricingOrder prior = existing.get();
            if (sameContent(prior, contract, request)) {
                return contractService.getById(contractId);
            }
            throw new ConflictException(
                    "外部点价号 " + request.externalPricingNo() + " 已用于内容不同的点价请求");
        }

        if (contract.getStatus() == ContractStatus.FINALLY_SETTLED) {
            throw new BusinessRuleException(
                    "合同已最终结算，拒绝迟到点价: " + contract.getContractNo());
        }

        BigDecimal unpriced = contract.unpricedQty();
        if (request.quantity().compareTo(unpriced) > 0) {
            throw new BusinessRuleException(
                    "点价数量超过未点价数量: 请求=" + request.quantity().toPlainString()
                            + ", 剩余=" + unpriced.toPlainString());
        }

        MarketPrice marketPrice = marketPriceService.requireExisting(
                request.priceSource(), request.priceVersion());
        validateAgainstContract(contract, marketPrice, request);

        PricingOrder order = new PricingOrder(contract, request.externalPricingNo(),
                request.quantity(), request.priceSource(), request.priceVersion(),
                marketPrice.getPrice(), request.premiumDiscount(), request.pricedAt());
        try {
            order = pricingOrderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    "外部点价号并发冲突: " + request.externalPricingNo());
        }

        int nextVersionNo = contract.getSettlementVersions().size() + 1;
        BigDecimal cumulative = latestCumulative(contract)
                .add(order.getAdjustmentAmount());
        SettlementVersion adjustment = new SettlementVersion(
                contract, nextVersionNo, SettlementVersionType.PRICING_ADJUSTMENT,
                order, order.getAdjustmentAmount(), cumulative, order.getPricedAt());
        settlementVersionRepository.save(adjustment);
        contract.getSettlementVersions().add(adjustment);
        contract.getPricingOrders().add(order);
        contract.addPricedQty(request.quantity());

        return contractService.getById(contractId);
    }

    private void validateAgainstContract(Contract contract, MarketPrice marketPrice,
                                         SubmitPricingRequest request) {
        if (!contract.allowsSource(marketPrice.getSource())) {
            throw new BusinessRuleException(
                    "市场价格来源不在合同允许列表: " + marketPrice.getSource());
        }
        if (!marketPrice.getCommodity().equals(contract.getCommodity())) {
            throw new BusinessRuleException(
                    "市场价格商品与合同商品不符: " + marketPrice.getCommodity()
                            + " != " + contract.getCommodity());
        }
        if (!marketPrice.getCurrency().equals(contract.getCurrency())) {
            throw new BusinessRuleException(
                    "市场价格币种与合同币种不符: " + marketPrice.getCurrency()
                            + " != " + contract.getCurrency());
        }
        if (!marketPrice.isValidAt(request.pricedAt())) {
            throw new BusinessRuleException(
                    "市场价格版本在点价时间已过期或尚未生效: "
                            + marketPrice.getSource() + "/" + marketPrice.getPriceVersion()
                            + ", 有效区间 " + marketPrice.getValidFrom() + " ~ "
                            + marketPrice.getValidTo());
        }
        if (request.pricedAt().isBefore(contract.getPricingWindowStart())
                || request.pricedAt().isAfter(contract.getPricingWindowEnd())) {
            throw new BusinessRuleException(
                    "点价时间超出合同点价窗口: " + contract.getPricingWindowStart()
                            + " ~ " + contract.getPricingWindowEnd());
        }
    }

    private boolean sameContent(PricingOrder prior, Contract contract,
                                SubmitPricingRequest request) {
        return prior.getContract().getId().equals(contract.getId())
                && prior.getQuantity().compareTo(request.quantity()) == 0
                && prior.getPriceSource().equals(request.priceSource())
                && prior.getPriceVersion().equals(request.priceVersion())
                && prior.getPremiumDiscount().compareTo(request.premiumDiscount()) == 0;
    }

    private BigDecimal latestCumulative(Contract contract) {
        return contract.getSettlementVersions().stream()
                .map(SettlementVersion::getCumulativeAdjustment)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }
}
