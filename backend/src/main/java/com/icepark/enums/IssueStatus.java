package com.icepark.enums;

public enum IssueStatus {
    ISSUED("已领用"),
    RETURNED("已归还"),
    FORCE_CLOSED("结束兜底归还");

    private final String label;

    IssueStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
