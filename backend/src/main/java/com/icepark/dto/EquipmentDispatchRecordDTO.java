package com.icepark.dto;

import com.icepark.enums.AgeGroup;
import com.icepark.enums.DispatchStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EquipmentDispatchRecordDTO {

    private Long id;
    private Long sessionId;
    private Long sessionEquipmentId;
    private Long equipmentId;
    private String equipmentCode;
    private String equipmentName;
    private String frostResistanceSpec;

    private String visitorName;
    private AgeGroup visitorAgeGroup;
    private String visitorAgeGroupLabel;

    private BigDecimal temperatureAtIssue;
    private BigDecimal frostLowerLimitAtIssue;

    private String issueOperator;
    private LocalDateTime issueTime;

    private DispatchStatus status;
    private String statusLabel;

    private String returnOperator;
    private LocalDateTime returnTime;
}
