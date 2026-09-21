package com.icepark.dto;

import com.icepark.enums.AgeGroup;
import com.icepark.enums.IssueStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class IssueRecordDTO {

    private Long id;
    private Long sessionId;
    private Long sessionEquipmentId;
    private Long equipmentId;
    private String equipmentCode;
    private String equipmentName;
    private String frostResistanceSpec;
    private BigDecimal limitTemperature;

    private String visitorId;
    private String visitorName;
    private AgeGroup visitorAgeGroup;
    private String visitorAgeGroupLabel;
    private BigDecimal measuredTemperature;

    private IssueStatus status;
    private String statusLabel;

    private String issueOperator;
    private LocalDateTime issueTime;
    private String returnOperator;
    private LocalDateTime returnTime;
    private String closeReason;
}
