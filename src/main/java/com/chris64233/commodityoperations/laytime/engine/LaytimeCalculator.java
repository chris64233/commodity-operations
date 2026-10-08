package com.chris64233.commodityoperations.laytime.engine;

import com.chris64233.commodityoperations.laytime.domain.CountingBasis;
import com.chris64233.commodityoperations.laytime.domain.Voyage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * 根据规范化时间线计算已用装卸时间、超出时间与滞期费。
 *
 * <p>口径：有效占用时间 = 起算时刻（靠泊或开工）到完工的总跨度，减去停算区间与
 * 该跨度相交的部分；停算区间已做并集合并，重叠暂停只扣除一次。滞期费按
 * 滞期秒数占一天（86400 秒）的比例乘以合同日费率，四舍五入到分（2 位小数）。</p>
 */
@Component
public class LaytimeCalculator {

    static final long SECONDS_PER_DAY = 86_400L;

    public LaytimeResult calculate(Voyage voyage, NormalizedTimeline timeline) {
        CountingBasis basis = voyage.getCountingBasis();
        Instant start = timeline.countingStart(basis);
        Instant end = timeline.completeAt();

        Duration gross = Duration.between(start, end);
        long suspendedSeconds = timeline.suspensions().stream()
                .mapToLong(s -> overlapSeconds(s.from(), s.to(), start, end))
                .sum();
        Duration suspended = Duration.ofSeconds(suspendedSeconds);
        Duration used = gross.minus(suspended);
        if (used.isNegative()) {
            throw new TimelineException(java.util.List.of(
                    "有效装卸时间为负：停算时间超过起算至完工的跨度，请检查事件时间线"));
        }

        Duration allowed = voyage.getAllowedLaytime();
        Duration excess = used.compareTo(allowed) > 0 ? used.minus(allowed) : Duration.ZERO;

        BigDecimal amount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        if (!excess.isZero()) {
            amount = voyage.getDemurrageRatePerDay()
                    .multiply(BigDecimal.valueOf(excess.getSeconds()))
                    .divide(BigDecimal.valueOf(SECONDS_PER_DAY), 2, RoundingMode.HALF_UP);
        }
        return new LaytimeResult(start, end, gross, suspended, used, allowed, excess, amount, voyage.getCurrency());
    }

    private long overlapSeconds(Instant from, Instant to, Instant windowStart, Instant windowEnd) {
        Instant lo = from.isAfter(windowStart) ? from : windowStart;
        Instant hi = to.isBefore(windowEnd) ? to : windowEnd;
        return hi.isAfter(lo) ? Duration.between(lo, hi).getSeconds() : 0L;
    }
}
