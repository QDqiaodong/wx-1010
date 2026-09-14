package com.icepark.dto;

import lombok.Data;

import java.util.List;

@Data
public class AgeGroupSummaryDTO {
    
    private String ageGroup;
    
    private String ageGroupLabel;
    
    private String ageRange;
    
    private Integer totalCount;
    
    private List<EquipmentDTO> equipments;
}