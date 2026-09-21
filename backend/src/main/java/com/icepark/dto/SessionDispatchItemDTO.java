package com.icepark.dto;

import com.icepark.enums.AgeGroup;
import com.icepark.enums.BindDispatchStatus;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 发装台现场视图：场次绑定的每一件器材一行，
 * 带上当前发装状态与当前未归还流水ID，前端据此展示"发装/归还"按钮。
 */
@Data
public class SessionDispatchItemDTO {

    private Long sessionEquipmentId;
    private Long equipmentId;
    private String equipmentCode;
    private String equipmentName;
    private String category;
    private String frostResistanceSpec;

    private AgeGroup targetAgeGroup;
    private String targetAgeGroupLabel;

    /** 从抗冻规格文本解析出的可承受下限气温（摄氏度），无法解析时为 null */
    private BigDecimal frostLowerLimit;

    private BindDispatchStatus dispatchStatus;
    private String dispatchStatusLabel;

    /** 当前未归还流水ID（在架时为 null），归还直接用它 */
    private Long activeRecordId;
    private String activeVisitorName;
    private AgeGroup activeVisitorAgeGroup;
    private String activeVisitorAgeGroupLabel;
}
