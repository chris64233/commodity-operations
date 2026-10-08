package com.chris64233.commodityoperations.contract;

import com.chris64233.commodityoperations.contract.ContractExceptions.ConflictException;
import com.chris64233.commodityoperations.contract.ContractExceptions.NotFoundException;
import com.chris64233.commodityoperations.contract.ContractExceptions.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class ContractService {

    private final ContractRepository contractRepository;
    private final DeliveryRepository deliveryRepository;
    private final ClosureRecordRepository closureRecordRepository;

    public ContractService(ContractRepository contractRepository,
                           DeliveryRepository deliveryRepository,
                           ClosureRecordRepository closureRecordRepository) {
        this.contractRepository = contractRepository;
        this.deliveryRepository = deliveryRepository;
        this.closureRecordRepository = closureRecordRepository;
    }

    @Transactional
    public Contract createContract(String commodity, BigDecimal baseQuantity,
                                   BigDecimal shortTolerancePercent, BigDecimal overTolerancePercent,
                                   LocalDate deliveryDeadline, String unit) {
        if (commodity == null || commodity.isBlank()) {
            throw new ValidationException("商品不能为空");
        }
        if (unit == null || unit.isBlank()) {
            throw new ValidationException("计量单位不能为空");
        }
        if (baseQuantity == null || baseQuantity.signum() <= 0) {
            throw new ValidationException("基础数量必须为正数");
        }
        if (shortTolerancePercent == null || shortTolerancePercent.signum() < 0
                || shortTolerancePercent.compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new ValidationException("短装比例必须在 [0, 100) 区间内");
        }
        if (overTolerancePercent == null || overTolerancePercent.signum() < 0) {
            throw new ValidationException("溢装比例不能为负数");
        }
        if (deliveryDeadline == null) {
            throw new ValidationException("交货期限不能为空");
        }
        return contractRepository.save(new Contract(commodity, baseQuantity, shortTolerancePercent,
                overTolerancePercent, deliveryDeadline, unit));
    }

    @Transactional
    public Delivery registerDelivery(Long contractId, String externalRef, String commodity, String unit,
                                     BigDecimal quantity, LocalDateTime deliveredAt) {
        Contract contract = lockContract(contractId);
        validateRefAndQuantity(externalRef, quantity, deliveredAt);
        String hash = hash("DELIVERY", contractId, externalRef, commodity, unit, quantity, deliveredAt, null);

        var existing = deliveryRepository.findByContractIdAndExternalRef(contractId, externalRef);
        if (existing.isPresent()) {
            Delivery found = existing.get();
            if (found.getPayloadHash().equals(hash)) {
                return found;
            }
            throw new ConflictException("相同外部单号但内容不一致，拒绝重放: " + externalRef);
        }

        if (contract.getStatus() != ContractStatus.OPEN) {
            throw new ConflictException("合同当前状态为 " + contract.getStatus() + "，不再接收交货");
        }
        if (!contract.getCommodity().equals(commodity)) {
            throw new ValidationException("交货商品与合同商品不一致: " + commodity);
        }
        if (!contract.getUnit().equals(unit)) {
            throw new ValidationException("交货单位与合同单位不一致: " + unit);
        }

        BigDecimal cumulative = cumulative(contractId);
        BigDecimal max = contract.maxAcceptableQuantity();
        if (cumulative.add(quantity).compareTo(max) > 0) {
            throw new ConflictException("累计交货将超过允许上限 " + max + "，当前累计 " + cumulative);
        }
        return deliveryRepository.save(new Delivery(contractId, externalRef, DeliveryType.DELIVERY,
                quantity, deliveredAt, null, hash));
    }

    @Transactional
    public Delivery reverseDelivery(Long contractId, Long deliveryId, String externalRef,
                                    LocalDateTime deliveredAt) {
        Contract contract = lockContract(contractId);
        if (externalRef == null || externalRef.isBlank()) {
            throw new ValidationException("外部单号不能为空");
        }
        if (deliveredAt == null) {
            throw new ValidationException("交货时间不能为空");
        }
        Delivery original = deliveryRepository.findById(deliveryId)
                .filter(d -> d.getContractId().equals(contractId))
                .orElseThrow(() -> new NotFoundException("交货记录不存在: " + deliveryId));
        if (original.getType() != DeliveryType.DELIVERY) {
            throw new ValidationException("只能冲销交货记录，不能冲销冲销记录");
        }
        String hash = hash("REVERSAL", contractId, externalRef, null, null,
                original.getQuantity(), deliveredAt, deliveryId);
        var existing = deliveryRepository.findByContractIdAndExternalRef(contractId, externalRef);
        if (existing.isPresent()) {
            Delivery found = existing.get();
            if (found.getPayloadHash().equals(hash)) {
                return found;
            }
            throw new ConflictException("相同外部单号但内容不一致，拒绝重放: " + externalRef);
        }
        if (deliveryRepository.existsByReversalOfId(deliveryId)) {
            throw new ConflictException("该交货记录已被冲销: " + deliveryId);
        }

        Delivery reversal = deliveryRepository.save(new Delivery(contractId, externalRef,
                DeliveryType.REVERSAL, original.getQuantity().negate(), deliveredAt, deliveryId, hash));

        BigDecimal cumulative = cumulative(contractId);
        if (contract.getStatus() == ContractStatus.CLOSED
                && cumulative.compareTo(contract.minAcceptableQuantity()) < 0) {
            contract.setStatus(ContractStatus.PENDING_REVIEW);
            closureRecordRepository
                    .findFirstByContractIdAndStateOrderByIdDesc(contractId, ClosureRecord.State.ACTIVE)
                    .ifPresent(record -> record.setState(ClosureRecord.State.REOPENED));
        }
        return reversal;
    }

    @Transactional
    public ClosureRecord closeContract(Long contractId) {
        Contract contract = lockContract(contractId);
        if (contract.getStatus() == ContractStatus.CLOSED) {
            return closureRecordRepository
                    .findFirstByContractIdAndStateOrderByIdDesc(contractId, ClosureRecord.State.ACTIVE)
                    .orElseThrow(() -> new ConflictException("合同已关闭但缺少关闭记录"));
        }
        if (contract.getStatus() == ContractStatus.PENDING_REVIEW) {
            throw new ConflictException("合同待复核，不能关闭");
        }
        BigDecimal cumulative = cumulative(contractId);
        BigDecimal min = contract.minAcceptableQuantity();
        if (cumulative.compareTo(min) < 0) {
            throw new ConflictException("累计交货 " + cumulative + " 未达到最低可接受数量 " + min + "，不能关闭");
        }
        contract.setStatus(ContractStatus.CLOSED);
        return closureRecordRepository.save(new ClosureRecord(contractId, cumulative));
    }

    @Transactional(readOnly = true)
    public ContractSummary getSummary(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new NotFoundException("合同不存在: " + contractId));
        BigDecimal cumulative = cumulative(contractId);
        BigDecimal max = contract.maxAcceptableQuantity();
        BigDecimal remaining = contract.getStatus() == ContractStatus.OPEN
                ? max.subtract(cumulative).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        return new ContractSummary(contract, contract.minAcceptableQuantity(), max, cumulative, remaining,
                deliveryRepository.findByContractIdOrderByIdAsc(contractId),
                closureRecordRepository.findByContractIdOrderByIdAsc(contractId));
    }

    private Contract lockContract(Long contractId) {
        return contractRepository.findByIdForUpdate(contractId)
                .orElseThrow(() -> new NotFoundException("合同不存在: " + contractId));
    }

    private BigDecimal cumulative(Long contractId) {
        return deliveryRepository.sumQuantityByContractId(contractId);
    }

    private static void validateRefAndQuantity(String externalRef, BigDecimal quantity,
                                               LocalDateTime deliveredAt) {
        if (externalRef == null || externalRef.isBlank()) {
            throw new ValidationException("外部单号不能为空");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new ValidationException("交货数量必须为正数");
        }
        if (deliveredAt == null) {
            throw new ValidationException("交货时间不能为空");
        }
    }

    private static String hash(String type, Long contractId, String externalRef, String commodity, String unit,
                               BigDecimal quantity, LocalDateTime deliveredAt, Long reversalOfId) {
        String raw = String.join("|", type, String.valueOf(contractId), externalRef,
                String.valueOf(commodity), String.valueOf(unit),
                quantity.stripTrailingZeros().toPlainString(), String.valueOf(deliveredAt),
                String.valueOf(reversalOfId));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record ContractSummary(Contract contract,
                                  BigDecimal minAcceptableQuantity,
                                  BigDecimal maxAcceptableQuantity,
                                  BigDecimal cumulativeQuantity,
                                  BigDecimal remainingReceivableQuantity,
                                  List<Delivery> deliveries,
                                  List<ClosureRecord> closureHistory) {
    }
}
