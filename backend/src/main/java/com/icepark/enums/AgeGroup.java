package com.icepark.enums;

public enum AgeGroup {
    CHILD("幼童", "3-7岁"),
    TEEN("青少年", "8-17岁"),
    ADULT("成人", "18岁以上");

    private final String label;
    private final String ageRange;

    AgeGroup(String label, String ageRange) {
        this.label = label;
        this.ageRange = ageRange;
    }

    public String getLabel() {
        return label;
    }

    public String getAgeRange() {
        return ageRange;
    }

    public static AgeGroup fromLabel(String label) {
        for (AgeGroup group : values()) {
            if (group.label.equals(label)) {
                return group;
            }
        }
        return null;
    }
}