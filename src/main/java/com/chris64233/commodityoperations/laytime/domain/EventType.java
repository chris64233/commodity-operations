package com.chris64233.commodityoperations.laytime.domain;

/**
 * 装卸作业事件类型。order 表示同一时刻发生多个事件时的规范先后顺序。
 */
public enum EventType {
    BERTH("靠泊", 0),
    START("开工", 1),
    PAUSE("暂停", 2),
    RESUME("复工", 3),
    COMPLETE("完工", 4);

    private final String label;
    private final int order;

    EventType(String label, int order) {
        this.label = label;
        this.order = order;
    }

    public String getLabel() {
        return label;
    }

    public int getOrder() {
        return order;
    }
}
