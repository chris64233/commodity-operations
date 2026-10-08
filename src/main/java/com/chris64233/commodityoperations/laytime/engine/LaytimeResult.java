package com.chris64233.commodityoperations.laytime.engine;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * 装卸时间与滞期费计算结果（时间口径见 README「时间计算口径」）。
 *
 * @param countingStart  起算时刻
 * @param completeAt     完工时刻
 * @param grossSpan      起算至完工的总跨度
 * @param suspended      停算合计（重叠已去重）
 * @param usedLaytime    已用有效装卸时间 = grossSpan - 与口径区间相交的停算时间
 * @param allowedLaytime 允许装卸时间
 * @param excess         超出时间 = max(0, usedLaytime - allowedLaytime)
 * @param demurrage      滞期费 = excess(秒) / 86400 * 日费率
 * @param currency       计价币种
 */
public record LaytimeResult(Instant countingStart,
                            Instant completeAt,
                            Duration grossSpan,
                            Duration suspended,
                            Duration usedLaytime,
                            Duration allowedLaytime,
                            Duration excess,
                            BigDecimal demurrage,
                            String currency) {
}
