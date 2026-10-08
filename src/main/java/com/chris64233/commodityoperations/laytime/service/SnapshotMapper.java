package com.chris64233.commodityoperations.laytime.service;

import com.chris64233.commodityoperations.laytime.calc.NormalizedTimeline;
import com.chris64233.commodityoperations.laytime.calc.RawEvent;
import com.chris64233.commodityoperations.laytime.calc.TimeInterval;
import com.chris64233.commodityoperations.laytime.domain.Settlement;
import com.chris64233.commodityoperations.laytime.service.SnapshotDtos.DifferenceEntry;
import com.chris64233.commodityoperations.laytime.service.SnapshotDtos.SettlementView;
import com.chris64233.commodityoperations.laytime.service.SnapshotDtos.SuspensionView;
import com.chris64233.commodityoperations.laytime.service.SnapshotDtos.TimelinePoint;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SnapshotMapper {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<TimelinePoint>> TIMELINE = new TypeReference<>() {
    };
    private static final TypeReference<List<SuspensionView>> SUSPENSIONS = new TypeReference<>() {
    };
    private static final TypeReference<List<DifferenceEntry>> DIFFERENCES = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public SnapshotMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("快照序列化失败", e);
        }
    }

    public List<TimelinePoint> timeline(NormalizedTimeline timeline) {
        return timeline.orderedEvents().stream()
                .map(e -> new TimelinePoint(e.externalEventNo(), e.type().name(), e.occurredAt()))
                .toList();
    }

    public List<SuspensionView> suspensions(List<TimeInterval> intervals) {
        return intervals.stream()
                .map(i -> new SuspensionView(i.from(), i.to(), i.durationSeconds(),
                        i.pauseEventNo(), i.resumeEventNo()))
                .toList();
    }

    public SettlementView toView(Settlement s, String voyageNo) {
        long gross = s.getCompleteAt().getEpochSecond() - s.getStartAt().getEpochSecond();
        return new SettlementView(
                s.getId(),
                voyageNo,
                s.getVersionNo(),
                s.getStatus().name(),
                s.isCurrent(),
                s.getPinnedEventVersion(),
                s.getAllowedLaytimeSeconds(),
                s.getDemurrageRatePerHour(),
                s.getCurrency(),
                s.getStopRule().name(),
                s.getStartAt(),
                s.getCompleteAt(),
                gross,
                gross - s.getUsedSeconds(),
                s.getUsedSeconds(),
                s.getExcessSeconds(),
                s.getDemurrageAmount(),
                read(s.getEventRefsJson(), STRING_LIST),
                read(s.getTimelineJson(), TIMELINE),
                read(s.getSuspensionsJson(), SUSPENSIONS),
                s.getBasedOnSettlementId(),
                s.getAdjustmentReason(),
                s.getDifferencesJson() == null ? List.of() : read(s.getDifferencesJson(), DIFFERENCES),
                s.getCreatedAt(),
                s.getConfirmedAt());
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("快照反序列化失败", e);
        }
    }

}
