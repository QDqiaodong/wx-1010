package com.icepark.dto;

import lombok.Data;

import java.util.List;

/**
 * 发装台总览：场次状态 + 本场绑定器材的在库/占用情况 + 本场发装流水。
 */
@Data
public class SessionIssueOverviewDTO {

    private Long sessionId;
    private String sessionCode;
    private String sessionName;
    private String sessionStatus;
    private String sessionStatusLabel;

    private List<BoundEquipmentItem> items;
    private List<IssueRecordDTO> records;

    @Data
    public static class BoundEquipmentItem {
        private Long sessionEquipmentId;
        private Long equipmentId;
        private String equipmentCode;
        private String equipmentName;
        private String frostResistanceSpec;
        private java.math.BigDecimal minTemperature;
        private com.icepark.enums.AgeGroup targetAgeGroup;
        private String targetAgeGroupLabel;
        /** null=在库可领；非null=当前占用流水ID */
        private Long currentIssueRecordId;
        /** 当前游客凭证号/姓名，便于界面直接看到“在谁手上” */
        private String currentVisitorId;
        private String currentVisitorName;
    }
}
