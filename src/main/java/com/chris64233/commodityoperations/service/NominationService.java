package com.chris64233.commodityoperations.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.chris64233.commodityoperations.domain.DailyCapacity;
import com.chris64233.commodityoperations.domain.LoadingContract;
import com.chris64233.commodityoperations.domain.NominationHistory;
import com.chris64233.commodityoperations.domain.NominationStatus;
import com.chris64233.commodityoperations.domain.VesselGuard;
import com.chris64233.commodityoperations.domain.VesselNomination;
import com.chris64233.commodityoperations.dto.CapacityRequest;
import com.chris64233.commodityoperations.dto.CapacityView;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NominationService {

    private final LoadingContractRepository contractRepository;
    private final DailyCapacityRepository capacityRepository;
    private final VesselNominationRepository nominationRepository;
    private final NominationHistoryRepository historyRepository;
    private final VesselGuardRepository vesselGuardRepository;

    public NominationService(LoadingContractRepository contractRepository,
                             DailyCapacityRepository capacityRepository,
                             VesselNominationRepository nominationRepository,
                             NominationHistoryRepository historyRepository,
                             VesselGuardRepository vesselGuardRepository) {
        this.contractRepository = contractRepository;
        this.capacityRepository = capacityRepository;
        this.nominationRepository = nominationRepository;
        this.historyRepository = historyRepository;
        this.vesselGuardRepository = vesselGuardRepository;
    }

    @Transactional
    public ContractView registerContract(ContractRequest request) {
        if (request.windowStart().isAfter(request.windowEnd())) {
            throw new BusinessRuleException("INVALID_WINDOW", "装期开始日期不能晚于结束日期");
        }
        if (contractRepository.existsByContractCode(request.contractCode())) {
            throw new BusinessRuleException("CONTRACT_DUPLICATED",
                    "合同编号已存在: " + request.contractCode());
        }
        LoadingContract contract = new LoadingContract(
                request.contractCode(),
                request.counterparty(),
                request.allowedQuantity(),
                request.windowStart(),
                request.windowEnd());
        contract = contractRepository.save(contract);
        return toContractView(contract, BigDecimal.ZERO);
    }

    @Transactional
    public CapacityView registerCapacity(CapacityRequest request) {
        capacityRepository.findByDate(request.date()).ifPresent(existing -> {
            throw new BusinessRuleException("CAPACITY_DUPLICATED",
                    "该日期的码头能力已登记: " + request.date());
        });
        DailyCapacity capacity = capacityRepository.save(
                new DailyCapacity(request.date(), request.availableCapacity()));
        return toCapacityView(capacity);
    }

    @Transactional
    public NominationView submit(NominationRequest request) {
        if (request.requestId() != null && !request.requestId().isBlank()) {
            var existing = nominationRepository.findByRequestId(request.requestId());
            if (existing.isPresent()) {
                return toNominationView(existing.get());
            }
        }

        LoadingContract contract = contractRepository.findByContractCode(request.contractCode())
                .orElseThrow(() -> new NotFoundException(
                        "合同不存在: " + request.contractCode()));
        LoadingContract lockedContract = contractRepository.findByIdForUpdate(contract.getId())
                .orElseThrow();

        LocalDate arrivalDate = request.estimatedArrival().toLocalDate();
        if (arrivalDate.isBefore(lockedContract.getWindowStart())
                || arrivalDate.isAfter(lockedContract.getWindowEnd())) {
            throw new BusinessRuleException("OUTSIDE_WINDOW",
                    "预计到港日期 " + arrivalDate + " 不在装期窗口 "
                            + lockedContract.getWindowStart() + " 至 "
                            + lockedContract.getWindowEnd() + " 内，不能进入确认流程");
        }

        BigDecimal committed = nominationRepository.sumActiveQuantity(lockedContract.getId());
        BigDecimal remaining = lockedContract.getAllowedQuantity().subtract(committed);
        if (request.plannedQuantity().compareTo(remaining) > 0) {
            throw new BusinessRuleException("QUANTITY_EXCEEDED",
                    "计划装货量 " + request.plannedQuantity() + " 超出合同剩余数量 " + remaining);
        }

        String normalizedRequestId = blankToNull(request.requestId());
        lockVessel(request.vesselCode());
        if (normalizedRequestId != null) {
            var concurrent = nominationRepository.findByRequestId(normalizedRequestId);
            if (concurrent.isPresent()) {
                return toNominationView(concurrent.get());
            }
        }
        if (nominationRepository.countActiveByVessel(request.vesselCode()) > 0) {
            throw new BusinessRuleException("VESSEL_BUSY",
                    "同一船舶已存在有效提报，不能同时占用两个计划: " + request.vesselCode());
        }

        VesselNomination nomination = nominationRepository.save(new VesselNomination(
                lockedContract.getId(),
                request.vesselCode(),
                request.vesselName(),
                request.estimatedArrival(),
                request.plannedQuantity(),
                normalizedRequestId));
        recordHistory(nomination.getId(), "SUBMITTED",
                "合同=" + lockedContract.getContractCode()
                        + ", 预计到港=" + request.estimatedArrival()
                        + ", 计划装货量=" + request.plannedQuantity());
        return toNominationView(nomination);
    }

    @Transactional
    public NominationView confirm(Long nominationId) {
        VesselNomination nomination = nominationRepository.findByIdForUpdate(nominationId)
                .orElseThrow(() -> new NotFoundException("提报不存在: " + nominationId));

        if (nomination.getStatus() == NominationStatus.CONFIRMED) {
            return toNominationView(nomination);
        }
        if (nomination.getStatus() == NominationStatus.WITHDRAWN || !nomination.isActiveFlag()) {
            throw new BusinessRuleException("NOMINATION_INACTIVE",
                    "提报已撤回，不能确认: " + nominationId);
        }

        LocalDate arrivalDate = nomination.getEstimatedArrival().toLocalDate();
        DailyCapacity capacity = capacityRepository.findByDateForUpdate(arrivalDate)
                .orElseThrow(() -> new BusinessRuleException("CAPACITY_NOT_FOUND",
                        "日期 " + arrivalDate + " 未登记码头能力，无法确认"));

        if (!capacity.canReserve(nomination.getPlannedQuantity())) {
            throw new BusinessRuleException("CAPACITY_EXCEEDED",
                    "日期 " + arrivalDate + " 码头能力不足：上限 "
                            + capacity.getAvailableCapacity() + "，已预留 "
                            + capacity.getReservedCapacity() + "，本次申请 "
                            + nomination.getPlannedQuantity());
        }

        capacity.reserve(nomination.getPlannedQuantity());
        nomination.markConfirmed();
        recordHistory(nomination.getId(), "CONFIRMED",
                "码头确认，占用 " + arrivalDate + " 能力 "
                        + nomination.getPlannedQuantity());
        return toNominationView(nomination);
    }

    @Transactional
    public NominationView withdraw(Long nominationId) {
        VesselNomination nomination = nominationRepository.findByIdForUpdate(nominationId)
                .orElseThrow(() -> new NotFoundException("提报不存在: " + nominationId));

        if (nomination.getStatus() == NominationStatus.WITHDRAWN) {
            return toNominationView(nomination);
        }
        if (nomination.getStatus() == NominationStatus.CONFIRMED) {
            throw new BusinessRuleException("ALREADY_CONFIRMED",
                    "提报已经码头确认，不能直接撤回，请使用改期流程: " + nominationId);
        }

        nomination.markWithdrawn();
        recordHistory(nomination.getId(), "WITHDRAWN", "合同方在确认前撤回提报");
        return toNominationView(nomination);
    }

    @Transactional
    public NominationView reschedule(Long nominationId, RescheduleRequest request) {
        VesselNomination nomination = nominationRepository.findByIdForUpdate(nominationId)
                .orElseThrow(() -> new NotFoundException("提报不存在: " + nominationId));

        if (nomination.getStatus() != NominationStatus.CONFIRMED || !nomination.isActiveFlag()) {
            throw new BusinessRuleException("NOT_CONFIRMED",
                    "只有已确认的计划才能改期: " + nominationId);
        }

        LocalDateTime newEta = request.newEstimatedArrival();
        LocalDate newDate = newEta.toLocalDate();
        LocalDate oldDate = nomination.getEstimatedArrival().toLocalDate();

        LoadingContract contract = contractRepository.findByIdForUpdate(nomination.getContractId())
                .orElseThrow();
        if (newDate.isBefore(contract.getWindowStart()) || newDate.isAfter(contract.getWindowEnd())) {
            throw new BusinessRuleException("OUTSIDE_WINDOW",
                    "改期日期 " + newDate + " 不在装期窗口内，原计划保持不变");
        }

        if (oldDate.equals(newDate)) {
            nomination.reschedule(newEta);
            recordHistory(nominationId, "RESCHEDULED",
                    "同日时段调整: " + oldDate + " -> " + newEta);
            return toNominationView(nomination);
        }

        DailyCapacity newCapacity = capacityRepository.findByDateForUpdate(newDate)
                .orElseThrow(() -> new BusinessRuleException("CAPACITY_NOT_FOUND",
                        "新时段 " + newDate + " 未登记码头能力，原计划保持不变"));

        if (!newCapacity.canReserve(nomination.getPlannedQuantity())) {
            throw new BusinessRuleException("CAPACITY_EXCEEDED",
                    "新时段 " + newDate + " 码头能力不足，原计划保持不变");
        }

        DailyCapacity oldCapacity = capacityRepository.findByDateForUpdate(oldDate)
                .orElseThrow(() -> new IllegalStateException(
                        "原时段能力记录缺失: " + oldDate));

        newCapacity.reserve(nomination.getPlannedQuantity());
        oldCapacity.release(nomination.getPlannedQuantity());
        nomination.reschedule(newEta);
        recordHistory(nominationId, "RESCHEDULED",
                "改期: " + oldDate + " -> " + newDate
                        + "，先占用新时段能力，再释放旧时段能力");
        return toNominationView(nomination);
    }

    @Transactional(readOnly = true)
    public ContractView getContract(String contractCode) {
        LoadingContract contract = contractRepository.findByContractCode(contractCode)
                .orElseThrow(() -> new NotFoundException("合同不存在: " + contractCode));
        BigDecimal committed = nominationRepository.sumActiveQuantity(contract.getId());
        return toContractView(contract, committed);
    }

    @Transactional(readOnly = true)
    public List<CapacityView> listCapacities() {
        return capacityRepository.findAllByOrderByDateAsc().stream()
                .map(this::toCapacityView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NominationView> listVesselPlans(String vesselCode) {
        return nominationRepository.findByVesselCodeAndActiveFlagTrueOrderByIdAsc(vesselCode)
                .stream()
                .map(this::toNominationView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HistoryView> listHistory(Long nominationId) {
        if (!nominationRepository.existsById(nominationId)) {
            throw new NotFoundException("提报不存在: " + nominationId);
        }
        return historyRepository.findByNominationIdOrderByEventTimeAscIdAsc(nominationId)
                .stream()
                .map(h -> new HistoryView(h.getId(), h.getNominationId(), h.getAction(),
                        h.getDetail(), h.getEventTime()))
                .toList();
    }

    private void lockVessel(String vesselCode) {
        vesselGuardRepository.findByCodeForUpdate(vesselCode)
                .orElseGet(() -> vesselGuardRepository.saveAndFlush(new VesselGuard(vesselCode)));
    }

    private void recordHistory(Long nominationId, String action, String detail) {
        historyRepository.save(new NominationHistory(nominationId, action, detail));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private ContractView toContractView(LoadingContract contract, BigDecimal committed) {
        return new ContractView(
                contract.getId(),
                contract.getContractCode(),
                contract.getCounterparty(),
                contract.getAllowedQuantity(),
                committed,
                contract.getAllowedQuantity().subtract(committed),
                contract.getWindowStart(),
                contract.getWindowEnd());
    }

    private CapacityView toCapacityView(DailyCapacity capacity) {
        return new CapacityView(
                capacity.getDate(),
                capacity.getAvailableCapacity(),
                capacity.getReservedCapacity(),
                capacity.getAvailableCapacity().subtract(capacity.getReservedCapacity()));
    }

    private NominationView toNominationView(VesselNomination nomination) {
        String contractCode = contractRepository.findById(nomination.getContractId())
                .map(LoadingContract::getContractCode)
                .orElse(null);
        return new NominationView(
                nomination.getId(),
                nomination.getContractId(),
                contractCode,
                nomination.getVesselCode(),
                nomination.getVesselName(),
                nomination.getEstimatedArrival(),
                nomination.getPlannedQuantity(),
                nomination.getStatus(),
                nomination.isActiveFlag());
    }
}
