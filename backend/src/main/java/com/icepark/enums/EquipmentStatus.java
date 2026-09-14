package com.icepark.enums;

public enum EquipmentStatus {
    AVAILABLE("可用"),
    IN_USE("使用中"),
    MAINTENANCE("维护中");

    private final String label;

    EquipmentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}