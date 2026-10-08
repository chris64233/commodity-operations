package com.chris64233.commodityoperations.laytime.domain;

/**
 * 装卸作业事件类型。
 */
public enum EventType {
    /** 靠泊（NOR 锚点）。 */
    BERTH,
    /** 开工。 */
    START,
    /** 暂停（停算开始）。 */
    SUSPEND,
    /** 复工（停算结束）。 */
    RESUME,
    /** 完工（装卸结束）。 */
    COMPLETE
}
