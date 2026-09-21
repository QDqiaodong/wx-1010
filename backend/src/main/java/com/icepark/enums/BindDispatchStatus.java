package com.icepark.enums;

/**
 * 场次-器材绑定的现场发装状态。
 * AVAILABLE：在架可领；ISSUED：已发给某位游客、尚未归还。
 */
public enum BindDispatchStatus {
    AVAILABLE("在架"),
    ISSUED("已领用");

    private final String label;

    BindDispatchStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
