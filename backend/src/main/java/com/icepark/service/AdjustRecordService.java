package com.icepark.service;

import com.icepark.dto.AdjustRecordDTO;
import com.icepark.entity.AdjustRecord;
import com.icepark.enums.AgeGroup;
import com.icepark.repository.AdjustRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdjustRecordService {
    
    private final AdjustRecordRepository adjustRecordRepository;
    
    public List<AdjustRecordDTO> getRecordsBySession(Long sessionId) {
        return adjustRecordRepository.findBySessionIdOrderByAdjustTimeDesc(sessionId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public List<AdjustRecordDTO> getAllRecords() {
        return adjustRecordRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    private AdjustRecordDTO convertToDTO(AdjustRecord record) {
        AdjustRecordDTO dto = new AdjustRecordDTO();
        dto.setId(record.getId());
        dto.setSessionId(record.getSessionId());
        dto.setEquipmentId(record.getEquipmentId());
        dto.setOldAgeGroup(record.getOldAgeGroup().name());
        dto.setNewAgeGroup(record.getNewAgeGroup().name());
        dto.setAdjustReason(record.getAdjustReason());
        dto.setAdjustOperator(record.getAdjustOperator());
        return dto;
    }
}