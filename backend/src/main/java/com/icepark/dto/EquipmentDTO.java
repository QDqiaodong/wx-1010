package com.icepark.dto;

import com.icepark.enums.AgeGroup;
import com.icepark.enums.EquipmentStatus;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class EquipmentDTO {
    
    private Long id;
    
    @NotBlank(message = "器材编号不能为空")
    @Size(max = 50, message = "器材编号长度不能超过50")
    private String equipmentCode;
    
    @NotBlank(message = "器材名称不能为空")
    @Size(max = 100, message = "器材名称长度不能超过100")
    private String name;
    
    @NotBlank(message = "耐寒规格不能为空")
    @Size(max = 200, message = "耐寒规格长度不能超过200")
    private String frostResistanceSpec;
    
    @NotBlank(message = "适配年龄段不能为空")
    private String ageGroup;
    
    @NotBlank(message = "器材类别不能为空")
    @Size(max = 50, message = "器材类别长度不能超过50")
    private String category;
    
    private String status;
}