package com.icepark.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SessionDTO {
    
    private Long id;
    
    @NotBlank(message = "场次编号不能为空")
    @Size(max = 50, message = "场次编号长度不能超过50")
    private String sessionCode;
    
    @NotBlank(message = "场次名称不能为空")
    @Size(max = 100, message = "场次名称长度不能超过100")
    private String sessionName;
    
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;
    
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;
    
    @NotNull(message = "幼童占比不能为空")
    private BigDecimal childRatio;
    
    @NotNull(message = "青少年占比不能为空")
    private BigDecimal teenRatio;
    
    @NotNull(message = "成人占比不能为空")
    private BigDecimal adultRatio;
    
    private String status;
}