package com.icepark.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
public class SessionEquipmentDTO {
    
    private Long id;
    
    @NotNull(message = "场次ID不能为空")
    private Long sessionId;
    
    @NotNull(message = "器材ID不能为空")
    private Long equipmentId;
    
    @NotNull(message = "目标年龄段不能为空")
    private String targetAgeGroup;
}