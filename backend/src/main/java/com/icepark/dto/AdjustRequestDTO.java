package com.icepark.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class AdjustRequestDTO {
    
    @NotNull(message = "场次ID不能为空")
    private Long sessionId;
    
    private BigDecimal childRatio;
    
    private BigDecimal teenRatio;
    
    private BigDecimal adultRatio;
    
    private String adjustReason;
    
    @NotNull(message = "操作人不能为空")
    private String adjustOperator;
}