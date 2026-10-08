package com.chris64233.commodityoperations.laytime.domain;

/**
 * 装卸时间起算口径（停算规则的一部分）。
 *
 * <ul>
 *   <li>{@link #ON_BERTH}：装卸时间自靠泊时刻起算（NOR 递交即起算）。</li>
 *   <li>{@link #ON_WORK}：装卸时间自实际开工时刻起算。</li>
 * </ul>
 * 无论哪种口径，作业过程中的暂停区间都不计入有效占用时间。
 */
public enum CountingBasis {
    ON_BERTH,
    ON_WORK
}
