package com.icepark.enums;

public enum SessionStatus {
    SCHEDULED("已安排"),
    IN_PROGRESS("进行中"),
    ENDED("已结束");

    private final String label;

    SessionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}