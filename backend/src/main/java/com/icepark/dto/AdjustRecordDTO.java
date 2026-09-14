package com.icepark.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class AdjustRecordDTO {
    
    private Long id;
    
    @NotNull(message = "场次ID不能为空")
    private Long sessionId;
    
    @NotNull(message = "器材ID不能为空")
    private Long equipmentId;
    
    @NotBlank(message = "变更前年龄段不能为空")
    private String oldAgeGroup;
    
    @NotBlank(message = "变更后年龄段不能为空")
    private String newAgeGroup;
    
    private String adjustReason;
    
    @NotBlank(message = "操作人不能为空")
    private String adjustOperator;
}