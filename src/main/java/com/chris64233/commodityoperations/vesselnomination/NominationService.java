package com.chris64233.commodityoperations.vesselnomination;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class NominationService {

    private static final Set<NominationStatus> ACTIVE_STATUSES =
            EnumSet.of(NominationStatus.PENDING, NominationStatus.CONFIRMED);

    private final LoadingContractRepository contractRepository;
    private final TerminalDailyCapacityRepository capacityRepository;
    private final VesselNominationRepository nominationRepository;
    private final NominationEventRepository eventRepository;

    public NominationService(LoadingContractRepository contractRepository,
                             TerminalDailyCapacityRepository capacityRepository,
                             VesselNominationRepository nominationRepository,
                             NominationEventRepository eventRepository) {
        this.contractRepository = contractRepository;
        this.capacityRepository = capacityRepository;
        this.nominationRepository = nominationRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public LoadingContract registerContract(String contractNumber, long allowedQuantity,
                                            LocalDate windowStart, LocalDate windowEnd) {
        if (allowedQuantity <= 0) {
            throw new NominationException("允许装运数量必须为正数");
        }
        if (windowEnd.isBefore(windowStart)) {
            throw new NominationException("装期窗口结束日期不能早于开始日期");
        }
        return contractRepository.save(new LoadingContract(contractNumber, allowedQuantity, windowStart, windowEnd));
    }

    @Transactional
    public TerminalDailyCapacity registerDailyCapacity(LocalDate date, long totalCapacity) {
        if (totalCapacity < 0) {
            throw new NominationException("码头每日能力不能为负数");
        }
        return capacityRepository.save(new TerminalDailyCapacity(date, totalCapacity));
    }

    @Transactional
    public VesselNomination submit(Long contractId, String vesselName, LocalDate eta, long plannedQuantity) {
        if (plannedQuantity <= 0) {
            throw new NominationException("计划装货量必须为正数");
        }
        LoadingContract contract = lockContract(contractId);
        if (!contract.covers(eta)) {
            throw new NominationException("预计到港时间超出合同装期窗口");
        }
        long remaining = remainingQuantityLocked(contract);
        if (plannedQuantity > remaining) {
            throw new NominationException("计划装货量超出合同剩余数量，剩余 " + remaining);
        }
        VesselNomination nomination = nominationRepository.save(
                new VesselNomination(contract, vesselName, eta, plannedQuantity));
        record(nomination.getId(), "SUBMITTED",
                "提交提报：船舶 " + vesselName + "，ETA " + eta + "，计划量 " + plannedQuantity);
        return nomination;
    }

    @Transactional
    public VesselNomination confirm(Long nominationId) {
        VesselNomination nomination = getNomination(nominationId);
        if (nomination.getStatus() == NominationStatus.CONFIRMED) {
            return nomination;
        }
        if (nomination.getStatus() != NominationStatus.PENDING) {
            throw new NominationException("当前状态不允许确认：" + nomination.getStatus());
        }
        lockContract(nomination.getContract().getId());
        nominationRepository.findFirstByVesselNameAndStatus(nomination.getVesselName(), NominationStatus.CONFIRMED)
                .ifPresent(existing -> {
                    throw new NominationException("船舶 " + nomination.getVesselName() + " 已存在有效计划，不能重复占用");
                });
        TerminalDailyCapacity capacity = lockCapacity(nomination.getEta());
        capacity.reserve(nomination.getPlannedQuantity());
        nomination.confirm(nomination.getEta());
        record(nomination.getId(), "CONFIRMED",
                "码头确认，预留 " + nomination.getEta() + " 能力 " + nomination.getPlannedQuantity());
        return nomination;
    }

    @Transactional
    public VesselNomination withdraw(Long nominationId) {
        VesselNomination nomination = getNomination(nominationId);
        if (nomination.getStatus() == NominationStatus.WITHDRAWN) {
            return nomination;
        }
        if (nomination.getStatus() != NominationStatus.PENDING) {
            throw new NominationException("已确认的提报不能撤回，请先改期或取消计划");
        }
        nomination.withdraw();
        record(nomination.getId(), "WITHDRAWN", "合同方在确认前撤回提报");
        return nomination;
    }

    @Transactional
    public VesselNomination reschedule(Long nominationId, LocalDate newDate) {
        VesselNomination nomination = getNomination(nominationId);
        if (nomination.getStatus() != NominationStatus.CONFIRMED) {
            throw new NominationException("只有已确认的提报才能改期");
        }
        LocalDate oldDate = nomination.getReservedDate();
        if (oldDate.equals(newDate)) {
            return nomination;
        }
        LoadingContract contract = lockContract(nomination.getContract().getId());
        if (!contract.covers(newDate)) {
            throw new NominationException("新时段超出合同装期窗口");
        }
        TerminalDailyCapacity newCapacity = lockCapacity(newDate);
        newCapacity.reserve(nomination.getPlannedQuantity());
        TerminalDailyCapacity oldCapacity = lockCapacity(oldDate);
        oldCapacity.release(nomination.getPlannedQuantity());
        nomination.moveTo(newDate);
        record(nomination.getId(), "RESCHEDULED", "改期：" + oldDate + " -> " + newDate);
        return nomination;
    }

    @Transactional(readOnly = true)
    public long remainingQuantity(Long contractId) {
        LoadingContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new NominationException("合同不存在：" + contractId));
        return remainingQuantityLocked(contract);
    }

    @Transactional(readOnly = true)
    public TerminalDailyCapacity dailyCapacity(LocalDate date) {
        return capacityRepository.findByCapacityDate(date)
                .orElseThrow(() -> new NominationException("码头 " + date + " 未登记能力"));
    }

    @Transactional(readOnly = true)
    public VesselNomination currentPlan(String vesselName) {
        return nominationRepository.findFirstByVesselNameAndStatus(vesselName, NominationStatus.CONFIRMED)
                .orElseThrow(() -> new NominationException("船舶 " + vesselName + " 当前没有有效计划"));
    }

    @Transactional(readOnly = true)
    public List<NominationEvent> history(Long nominationId) {
        return eventRepository.findByNominationIdOrderByOccurredAtAscIdAsc(nominationId);
    }

    private LoadingContract lockContract(Long contractId) {
        return contractRepository.findByIdForUpdate(contractId)
                .orElseThrow(() -> new NominationException("合同不存在：" + contractId));
    }

    private TerminalDailyCapacity lockCapacity(LocalDate date) {
        return capacityRepository.findByCapacityDateForUpdate(date)
                .orElseThrow(() -> new NominationException("码头 " + date + " 未登记可用能力"));
    }

    private VesselNomination getNomination(Long nominationId) {
        return nominationRepository.findById(nominationId)
                .orElseThrow(() -> new NominationException("提报不存在：" + nominationId));
    }

    private long remainingQuantityLocked(LoadingContract contract) {
        long used = nominationRepository.sumPlannedQuantityByContractAndStatusIn(contract.getId(), ACTIVE_STATUSES);
        return contract.getAllowedQuantity() - used;
    }

    private void record(Long nominationId, String type, String detail) {
        eventRepository.save(new NominationEvent(nominationId, type, detail));
    }
}
