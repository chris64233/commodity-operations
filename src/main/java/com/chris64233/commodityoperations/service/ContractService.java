package com.chris64233.commodityoperations.service;

import com.chris64233.commodityoperations.dto.ContractRequests.CreateContractRequest;
import com.chris64233.commodityoperations.dto.ContractRequests.RegisterDeliveryRequest;
import com.chris64233.commodityoperations.dto.ContractRequests.ReverseDeliveryRequest;
import com.chris64233.commodityoperations.dto.ContractViews.ClosureView;
import com.chris64233.commodityoperations.dto.ContractViews.ContractView;
import com.chris64233.commodityoperations.dto.ContractViews.DeliveryView;
import com.chris64233.commodityoperations.model.ClosureRecord;
import com.chris64233.commodityoperations.model.ContractStatus;
import com.chris64233.commodityoperations.model.DeliveryRecord;
import com.chris64233.commodityoperations.model.DeliveryType;
import com.chris64233.commodityoperations.model.SpotContract;
import com.chris64233.commodityoperations.repository.ClosureRecordRepository;
import com.chris64233.commodityoperations.repository.DeliveryRecordRepository;
import com.chris64233.commodityoperations.repository.SpotContractRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class ContractService {

    private final SpotContractRepository contractRepository;
    private final DeliveryRecordRepository deliveryRepository;
    private final ClosureRecordRepository closureRepository;

    public ContractService(SpotContractRepository contractRepository,
                           DeliveryRecordRepository deliveryRepository,
                           ClosureRecordRepository closureRepository) {
        this.contractRepository = contractRepository;
        this.deliveryRepository = deliveryRepository;
        this.closureRepository = closureRepository;
    }

    @Transactional
    public ContractView createContract(CreateContractRequest request) {
        SpotContract contract = contractRepository.save(new SpotContract(
                request.commodity(), request.baseQuantity(), request.shortTolerancePercent(),
                request.overTolerancePercent(), request.deliveryDeadline(), request.unit()));
        return toView(contract);
    }

    @Transactional
    public DeliveryView registerDelivery(Long contractId, RegisterDeliveryRequest request) {
        SpotContract contract = lockContract(contractId);

        var existing = deliveryRepository.findByContractIdAndExternalRef(contractId, request.externalRef());
        if (existing.isPresent()) {
            DeliveryRecord record = existing.get();
            if (record.getType() == DeliveryType.DELIVERY
                    && record.getQuantity().compareTo(request.quantity()) == 0
                    && record.getDeliveryDate().equals(request.deliveryDate())) {
                return toDeliveryView(record);
            }
            throw ContractException.conflict(
                    "外部单号 " + request.externalRef() + " 已存在且内容不一致");
        }

        if (!contract.getCommodity().equals(request.commodity())
                || !contract.getUnit().equals(request.unit())) {
            throw ContractException.unprocessable("交货商品或计量单位与合同不一致，不能混入同一合同");
        }
        if (contract.getStatus() != ContractStatus.OPEN) {
            throw ContractException.conflict("合同已关闭，迟到交货不得入账");
        }
        if (request.deliveryDate().isAfter(contract.getDeliveryDeadline())) {
            throw ContractException.unprocessable("交货时间超出合同交货期限");
        }

        BigDecimal cumulative = deliveryRepository.sumQuantityByContractId(contractId);
        if (cumulative.add(request.quantity()).compareTo(contract.maxAcceptableQuantity()) > 0) {
            throw ContractException.conflict("累计交货数量将超过合同允许的溢装上限");
        }

        DeliveryRecord record = new DeliveryRecord(contractId, request.externalRef(),
                request.quantity(), request.deliveryDate(), DeliveryType.DELIVERY, null);
        try {
            return toDeliveryView(deliveryRepository.saveAndFlush(record));
        } catch (DataIntegrityViolationException e) {
            throw ContractException.conflict("外部单号 " + request.externalRef() + " 已被并发登记");
        }
    }

    @Transactional
    public ContractView closeContract(Long contractId) {
        SpotContract contract = lockContract(contractId);
        if (contract.getStatus() != ContractStatus.OPEN) {
            throw ContractException.conflict("合同当前状态不允许关闭: " + contract.getStatus());
        }
        BigDecimal cumulative = deliveryRepository.sumQuantityByContractId(contractId);
        if (cumulative.compareTo(contract.minAcceptableQuantity()) < 0) {
            throw ContractException.unprocessable("累计交货数量未达到最低可接受数量，不能关闭合同");
        }
        if (cumulative.compareTo(contract.maxAcceptableQuantity()) > 0) {
            throw ContractException.unprocessable("累计交货数量超出允许上限，不能关闭合同");
        }
        contract.setStatus(ContractStatus.CLOSED);
        closureRepository.save(new ClosureRecord(contractId, cumulative, Instant.now()));
        return toView(contract);
    }

    @Transactional
    public DeliveryView reverseDelivery(Long contractId, Long deliveryId, ReverseDeliveryRequest request) {
        SpotContract contract = lockContract(contractId);

        var existing = deliveryRepository.findByContractIdAndExternalRef(contractId, request.externalRef());
        if (existing.isPresent()) {
            DeliveryRecord record = existing.get();
            if (record.getType() == DeliveryType.REVERSAL
                    && deliveryId.equals(record.getReversalOfId())
                    && record.getQuantity().compareTo(request.quantity().negate()) == 0
                    && record.getDeliveryDate().equals(request.reversalDate())) {
                return toDeliveryView(record);
            }
            throw ContractException.conflict(
                    "外部单号 " + request.externalRef() + " 已存在且内容不一致");
        }

        DeliveryRecord original = deliveryRepository.findById(deliveryId)
                .filter(d -> d.getContractId().equals(contractId) && d.getType() == DeliveryType.DELIVERY)
                .orElseThrow(() -> ContractException.notFound("交货记录不存在: " + deliveryId));

        BigDecimal alreadyReversed = deliveryRepository.sumReversedByDeliveryId(deliveryId, DeliveryType.REVERSAL);
        if (alreadyReversed.add(request.quantity()).compareTo(original.getQuantity()) > 0) {
            throw ContractException.unprocessable("冲销数量超过原交货记录的未冲销余额");
        }

        DeliveryRecord reversal = new DeliveryRecord(contractId, request.externalRef(),
                request.quantity().negate(), request.reversalDate(), DeliveryType.REVERSAL, deliveryId);
        DeliveryView view;
        try {
            view = toDeliveryView(deliveryRepository.saveAndFlush(reversal));
        } catch (DataIntegrityViolationException e) {
            throw ContractException.conflict("外部单号 " + request.externalRef() + " 已被并发登记");
        }

        if (contract.getStatus() == ContractStatus.CLOSED) {
            BigDecimal cumulative = deliveryRepository.sumQuantityByContractId(contractId);
            if (cumulative.compareTo(contract.minAcceptableQuantity()) < 0
                    || cumulative.compareTo(contract.maxAcceptableQuantity()) > 0) {
                contract.setStatus(ContractStatus.PENDING_REVIEW);
                closureRepository.findByContractIdOrderByIdAsc(contractId).stream()
                        .filter(c -> !c.isSuperseded())
                        .forEach(ClosureRecord::markSuperseded);
            }
        }
        return view;
    }

    @Transactional(readOnly = true)
    public ContractView getContract(Long contractId) {
        SpotContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> ContractException.notFound("合同不存在: " + contractId));
        return toView(contract);
    }

    private SpotContract lockContract(Long contractId) {
        return contractRepository.findByIdForUpdate(contractId)
                .orElseThrow(() -> ContractException.notFound("合同不存在: " + contractId));
    }

    private ContractView toView(SpotContract contract) {
        BigDecimal cumulative = deliveryRepository.sumQuantityByContractId(contract.getId());
        BigDecimal remaining = contract.getStatus() == ContractStatus.OPEN
                ? contract.maxAcceptableQuantity().subtract(cumulative).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        List<DeliveryView> deliveries = deliveryRepository
                .findByContractIdOrderByIdAsc(contract.getId()).stream()
                .map(this::toDeliveryView)
                .toList();
        List<ClosureView> closures = closureRepository
                .findByContractIdOrderByIdAsc(contract.getId()).stream()
                .map(c -> new ClosureView(c.getId(), c.getCumulativeQuantity(), c.getClosedAt(), c.isSuperseded()))
                .toList();
        return new ContractView(contract.getId(), contract.getCommodity(), contract.getUnit(),
                contract.getBaseQuantity(), contract.getShortTolerancePercent(),
                contract.getOverTolerancePercent(), contract.minAcceptableQuantity(),
                contract.maxAcceptableQuantity(), cumulative, remaining,
                contract.getDeliveryDeadline(), contract.getStatus(), deliveries, closures);
    }

    private DeliveryView toDeliveryView(DeliveryRecord record) {
        return new DeliveryView(record.getId(), record.getExternalRef(), record.getQuantity(),
                record.getDeliveryDate(), record.getType(), record.getReversalOfId());
    }
}
