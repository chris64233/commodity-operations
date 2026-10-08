package com.chris64233.commodityoperations.laytime.engine;

import java.util.List;

/**
 * 时间线无法解释时抛出，消息中必须指出具体问题。
 */
public class TimelineException extends RuntimeException {

    private final List<String> problems;

    public TimelineException(List<String> problems) {
        super(String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> getProblems() {
        return problems;
    }
}
