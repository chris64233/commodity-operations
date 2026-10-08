package com.chris64233.commodityoperations.laytime.service;

import com.chris64233.commodityoperations.laytime.domain.EventType;
import com.chris64233.commodityoperations.laytime.domain.SettlementStatus;
import com.chris64233.commodityoperations.laytime.domain.StopRule;
import com.chris64233.commodityoperations.laytime.exception.EventConflictException;
import com.chris64233.commodityoperations.laytime.exception.LaytimeValidationException;
import com.chris64233.commodityoperations.laytime.exception.SettlementConflictException;
import com.chris64233.commodityoperations.laytime.repository.SettlementRepository;
import com.chris64233.commodityoperations.laytime.service.SnapshotDtos.DifferenceEntry;
import com.chris64233.commodityoperations.laytime.service.SnapshotDtos.SettlementView;
import com.chris64233.commodityoperations.laytime.service.SnapshotDtos.VoyageDetailView;
import com.chris64233.commodityoperations.laytime.web.EventRequest;
import com.chris64233.commodityoperations.laytime.web.VoyageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LaytimeServiceIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private LaytimeService service;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private com.chris64233.commodityoperations.laytime.repository.VoyageRepository voyageRepository;

    private String registerVoyage(String no) {
        service.registerVoyage(new VoyageRequest(
                no, "PT6H", new BigDecimal("100.00"), "USD",
                StopRule.DEDUCT_PAUSE_INTERVALS.name()));
        return no;
    }

    private void event(String voyageNo, String externalNo, EventType type, int hours) {
        service.ingestEvent(voyageNo,
                new EventRequest(externalNo, type.name(), T0.plusSeconds(hours * 3600L)));
    }

    private Map<String, SettlementView> versions(VoyageDetailView detail) {
        return detail.settlements().stream()
                .collect(Collectors.toMap(v -> "V" + v.versionNo(), Function.identity()));
    }

    @Test
    void 完整流程_重放幂等_滞期费计算正确() {
        String no = registerVoyage("V-FULL");
        // 08 靠泊，10 开工，12-13 暂停，18 完工 -> 有效 7h，允许 6h，超 1h
        event(no, "e1", EventType.BERTH, 8);
        event(no, "e2", EventType.START, 10);
        event(no, "e3", EventType.PAUSE, 12);
        event(no, "e4", EventType.RESUME, 13);
        event(no, "e5", EventType.COMPLETE, 18);

        assertThat(service.ingestEvent(no,
                new EventRequest("e3", EventType.PAUSE.name(), T0.plusSeconds(12 * 3600L))))
                .isFalse();

        SettlementView draft = service.generateSettlement(no, null);
        assertThat(draft.versionNo()).isEqualTo(1);
        assertThat(draft.usedSeconds()).isEqualTo(7 * 3600L);
        assertThat(draft.excessSeconds()).isEqualTo(3600L);
        assertThat(draft.demurrageAmount()).isEqualByComparingTo("100.00");
        assertThat(draft.eventRefs()).containsExactly("e1", "e2", "e3", "e4", "e5");
        assertThat(draft.timeline()).hasSize(5);
        assertThat(draft.suspensions()).hasSize(1);

        SettlementView confirmed = service.confirmSettlement(no, null);
        assertThat(confirmed.status()).isEqualTo(SettlementStatus.CONFIRMED.name());
        assertThat(confirmed.confirmedAt()).isNotNull();
    }

    @Test
    void 同号内容变化返回冲突() {
        String no = registerVoyage("V-CONFLICT");
        event(no, "e1", EventType.BERTH, 8);

        assertThatThrownBy(() -> service.ingestEvent(no,
                new EventRequest("e1", EventType.BERTH.name(), T0.plusSeconds(9 * 3600L))))
                .isInstanceOf(EventConflictException.class)
                .hasMessageContaining("e1")
                .hasMessageContaining("冲突");

        assertThatThrownBy(() -> service.ingestEvent(no,
                new EventRequest("e1", EventType.START.name(), T0.plusSeconds(8 * 3600L))))
                .isInstanceOf(EventConflictException.class);
    }

    @Test
    void 迟到事件触发重算生成差额调整版本且原金额保留() {
        String no = registerVoyage("V-LATE");
        // 初版：10-18 全程无暂停，有效 8h，超 2h -> 200 USD
        event(no, "e1", EventType.BERTH, 8);
        event(no, "e2", EventType.START, 10);
        event(no, "e5", EventType.COMPLETE, 18);

        SettlementView v1 = service.generateSettlement(no, null);
        service.confirmSettlement(no, null);
        assertThat(v1.demurrageAmount()).isEqualByComparingTo("200.00");

        // 迟到事件：12-14 停工，有效变 6h，无滞期
        event(no, "e3", EventType.PAUSE, 12);
        event(no, "e4", EventType.RESUME, 14);

        assertThatThrownBy(() -> service.generateSettlement(no, null))
                .isInstanceOf(SettlementConflictException.class)
                .hasMessageContaining("调整原因");

        SettlementView v2 = service.generateSettlement(no, "港口补传 12:00-14:00 停工记录");
        assertThat(v2.versionNo()).isEqualTo(2);
        assertThat(v2.current()).isTrue();
        assertThat(v2.status()).isEqualTo(SettlementStatus.DRAFT.name());
        assertThat(v2.basedOnSettlementId()).isEqualTo(v1.id());
        assertThat(v2.adjustmentReason()).isEqualTo("港口补传 12:00-14:00 停工记录");
        assertThat(v2.pinnedEventVersion()).isEqualTo(5);
        assertThat(v2.usedSeconds()).isEqualTo(6 * 3600L);
        assertThat(v2.excessSeconds()).isZero();
        assertThat(v2.demurrageAmount()).isEqualByComparingTo("0.00");

        Map<String, DifferenceEntry> diffs = v2.differences().stream()
                .collect(Collectors.toMap(DifferenceEntry::item, Function.identity()));
        assertThat(diffs.get("USED_SECONDS").delta()).isEqualByComparingTo("-7200");
        assertThat(diffs.get("DEMURRAGE_AMOUNT").oldValue()).isEqualByComparingTo("200.00");
        assertThat(diffs.get("DEMURRAGE_AMOUNT").newValue()).isEqualByComparingTo("0.00");
        assertThat(diffs.get("DEMURRAGE_AMOUNT").delta()).isEqualByComparingTo("-200.00");

        VoyageDetailView detail = service.getVoyageDetail(no);
        Map<String, SettlementView> versions = versions(detail);
        assertThat(versions.get("V1").status()).isEqualTo(SettlementStatus.CONFIRMED.name());
        assertThat(versions.get("V1").current()).isFalse();
        assertThat(versions.get("V1").demurrageAmount()).isEqualByComparingTo("200.00");
        assertThat(versions.get("V2").current()).isTrue();
        assertThat(detail.rawEvents()).hasSize(5);
        assertThat(detail.eventVersion()).isEqualTo(5);
    }

    @Test
    void 已确认后迟到事件不覆盖原结算() {
        String no = registerVoyage("V-COVER");
        event(no, "e1", EventType.BERTH, 8);
        event(no, "e2", EventType.START, 10);
        event(no, "e5", EventType.COMPLETE, 18);
        SettlementView v1 = service.generateSettlement(no, null);
        service.confirmSettlement(no, null);

        // 事件版本已变化但不重算：查询中原确认结算金额与状态保持不变，且不存在草稿
        event(no, "e6", EventType.PAUSE, 12);
        event(no, "e7", EventType.RESUME, 13);

        VoyageDetailView detail = service.getVoyageDetail(no);
        assertThat(detail.settlements()).hasSize(1);
        SettlementView stillV1 = detail.settlements().get(0);
        assertThat(stillV1.id()).isEqualTo(v1.id());
        assertThat(stillV1.status()).isEqualTo(SettlementStatus.CONFIRMED.name());
        assertThat(stillV1.demurrageAmount()).isEqualByComparingTo("200.00");
        assertThat(stillV1.pinnedEventVersion()).isEqualTo(3);
    }

    @Test
    void 失败重算不产生半张结算单且已确认金额不变() {
        String no = registerVoyage("V-FAIL");
        event(no, "e1", EventType.BERTH, 8);
        event(no, "e2", EventType.START, 10);
        event(no, "e5", EventType.COMPLETE, 18);
        SettlementView v1 = service.generateSettlement(no, null);
        service.confirmSettlement(no, null);

        // 无法解释的迟到事件：19 点暂停（完工之后）
        event(no, "e8", EventType.PAUSE, 19);

        long countBefore = settlementRepository
                .findByVoyage_IdOrderByVersionNoAsc(findVoyageId(no)).size();
        assertThatThrownBy(() -> service.generateSettlement(no, "坏数据"))
                .isInstanceOf(LaytimeValidationException.class)
                .hasMessageContaining("完工之后");

        assertThat(settlementRepository.findByVoyage_IdOrderByVersionNoAsc(findVoyageId(no)).size())
                .isEqualTo(countBefore);
        VoyageDetailView detail = service.getVoyageDetail(no);
        assertThat(detail.settlements()).hasSize(1);
        assertThat(detail.settlements().get(0).id()).isEqualTo(v1.id());
        assertThat(detail.settlements().get(0).demurrageAmount()).isEqualByComparingTo("200.00");
        assertThat(detail.settlements().get(0).status())
                .isEqualTo(SettlementStatus.CONFIRMED.name());
    }

    @Test
    void 时间线不完整时拒绝结算() {
        String no = registerVoyage("V-INCOMPLETE");
        event(no, "e1", EventType.BERTH, 8);
        event(no, "e2", EventType.START, 10);

        assertThatThrownBy(() -> service.generateSettlement(no, null))
                .isInstanceOf(LaytimeValidationException.class)
                .hasMessageContaining("完工");
        assertThat(settlementRepository.findByVoyage_IdOrderByVersionNoAsc(
                findVoyageId(no))).isEmpty();
    }

    private Long findVoyageId(String no) {
        return voyageRepository.findByVoyageNo(no).orElseThrow().getId();
    }

    @Test
    void 基于过期事件版本的草稿在有新事件时不能确认() {
        String no = registerVoyage("V-STALE");
        event(no, "e1", EventType.BERTH, 8);
        event(no, "e2", EventType.START, 10);
        event(no, "e5", EventType.COMPLETE, 18);
        service.generateSettlement(no, null);

        event(no, "e6", EventType.PAUSE, 12);
        event(no, "e7", EventType.RESUME, 13);

        assertThatThrownBy(() -> service.confirmSettlement(no, null))
                .isInstanceOf(SettlementConflictException.class)
                .hasMessageContaining("最新完整事件版本");

        // 重新基于最新版本生成草稿后可以确认
        SettlementView regenerated = service.generateSettlement(no, null);
        assertThat(regenerated.versionNo()).isEqualTo(2);
        assertThat(regenerated.usedSeconds()).isEqualTo(7 * 3600L);
        SettlementView confirmed = service.confirmSettlement(no, null);
        assertThat(confirmed.status()).isEqualTo(SettlementStatus.CONFIRMED.name());
        assertThat(confirmed.versionNo()).isEqualTo(2);
    }
}
