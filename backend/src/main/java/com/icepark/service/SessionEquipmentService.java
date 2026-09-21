package com.icepark.service;

import com.icepark.dto.AdjustRequestDTO;
import com.icepark.dto.SessionEquipmentDTO;
import com.icepark.entity.AdjustRecord;
import com.icepark.entity.Equipment;
import com.icepark.entity.Session;
import com.icepark.entity.SessionEquipment;
import com.icepark.enums.AgeGroup;
import com.icepark.enums.BindDispatchStatus;
import com.icepark.enums.EquipmentStatus;
import com.icepark.repository.AdjustRecordRepository;
import com.icepark.repository.EquipmentRepository;
import com.icepark.repository.SessionEquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionEquipmentService {
    
    private final SessionEquipmentRepository sessionEquipmentRepository;
    private final EquipmentRepository equipmentRepository;
    private final AdjustRecordRepository adjustRecordRepository;
    private final SessionService sessionService;
    private final EquipmentDispatchService equipmentDispatchService;
    
    @Transactional
    public SessionEquipmentDTO bindEquipment(Long sessionId, Long equipmentId, String targetAgeGroup) {
        if (sessionEquipmentRepository.existsBySessionIdAndEquipmentId(sessionId, equipmentId)) {
            throw new RuntimeException("该器材已绑定到场次");
        }

        // 已在其他进行中场次发给游客且未归还的器材，不允许绑定到新场次
        equipmentDispatchService.assertEquipmentNotOutstanding(equipmentId);

        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new RuntimeException("器材不存在，ID: " + equipmentId));

        SessionEquipment sessionEquipment = new SessionEquipment();
        sessionEquipment.setSessionId(sessionId);
        sessionEquipment.setEquipmentId(equipmentId);
        sessionEquipment.setTargetAgeGroup(AgeGroup.valueOf(targetAgeGroup.toUpperCase()));
        sessionEquipment.setDispatchStatus(BindDispatchStatus.AVAILABLE);

        SessionEquipment saved = sessionEquipmentRepository.save(sessionEquipment);

        equipment.setStatus(EquipmentStatus.IN_USE);
        equipmentRepository.save(equipment);

        return convertToDTO(saved);
    }
    
    public List<SessionEquipmentDTO> getSessionEquipments(Long sessionId) {
        return sessionEquipmentRepository.findBySessionId(sessionId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void unbindEquipment(Long sessionId, Long equipmentId) {
        // 已发出未归还的器材不允许解绑，避免流水悬空、器材无法归还
        equipmentDispatchService.assertNotOutstandingInSession(sessionId, equipmentId);

        SessionEquipment sessionEquipment = sessionEquipmentRepository.findBySessionId(sessionId).stream()
                .filter(se -> se.getEquipmentId().equals(equipmentId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("该器材未绑定到场次"));
        
        sessionEquipmentRepository.delete(sessionEquipment);
        
        Equipment equipment = equipmentRepository.findById(equipmentId).orElse(null);
        if (equipment != null) {
            boolean isUsedElsewhere = sessionEquipmentRepository.findByEquipmentId(equipmentId).stream()
                    .anyMatch(se -> !se.getSessionId().equals(sessionId));
            
            if (!isUsedElsewhere) {
                equipment.setStatus(EquipmentStatus.AVAILABLE);
                equipmentRepository.save(equipment);
            }
        }
    }
    
    @Transactional
    public void adjustEquipmentAgeGroup(Long sessionId, Long equipmentId, 
                                         String newAgeGroup, String adjustReason, String operator) {
        SessionEquipment sessionEquipment = sessionEquipmentRepository.findBySessionId(sessionId).stream()
                .filter(se -> se.getEquipmentId().equals(equipmentId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("该器材未绑定到场次"));
        
        AgeGroup oldAgeGroup = sessionEquipment.getTargetAgeGroup();
        AgeGroup newGroup = AgeGroup.valueOf(newAgeGroup.toUpperCase());
        
        if (oldAgeGroup == newGroup) {
            throw new RuntimeException("新年龄段与原年龄段相同");
        }
        
        sessionEquipment.setTargetAgeGroup(newGroup);
        sessionEquipmentRepository.save(sessionEquipment);
        
        AdjustRecord record = new AdjustRecord();
        record.setSessionId(sessionId);
        record.setEquipmentId(equipmentId);
        record.setOldAgeGroup(oldAgeGroup);
        record.setNewAgeGroup(newGroup);
        record.setAdjustReason(adjustReason);
        record.setAdjustOperator(operator);
        adjustRecordRepository.save(record);
        
        log.info("器材年龄段调整: 场次{} 器材{} 从{}调整为{}", sessionId, equipmentId, oldAgeGroup, newGroup);
    }
    
    @Transactional
    public List<AdjustRecord> autoAdjustByRatio(AdjustRequestDTO request) {
        Long sessionId = request.getSessionId();
        Session session = sessionService.getSessionEntity(sessionId);
        
        BigDecimal childRatio = request.getChildRatio() != null ? request.getChildRatio() : session.getChildRatio();
        BigDecimal teenRatio = request.getTeenRatio() != null ? request.getTeenRatio() : session.getTeenRatio();
        BigDecimal adultRatio = request.getAdultRatio() != null ? request.getAdultRatio() : session.getAdultRatio();
        
        session.setChildRatio(childRatio);
        session.setTeenRatio(teenRatio);
        session.setAdultRatio(adultRatio);
        
        List<SessionEquipment> currentBindings = sessionEquipmentRepository.findBySessionId(sessionId);
        
        Map<AgeGroup, List<SessionEquipment>> groupedByAge = currentBindings.stream()
                .collect(Collectors.groupingBy(SessionEquipment::getTargetAgeGroup));
        
        int totalEquipment = currentBindings.size();
        int childCount = (int) Math.round(childRatio.multiply(BigDecimal.valueOf(totalEquipment)).doubleValue());
        int teenCount = (int) Math.round(teenRatio.multiply(BigDecimal.valueOf(totalEquipment)).doubleValue());
        int adultCount = totalEquipment - childCount - teenCount;
        
        List<AdjustRecord> records = new ArrayList<>();
        
        redistributeEquipments(AgeGroup.CHILD, groupedByAge.getOrDefault(AgeGroup.CHILD, new ArrayList<>()), 
                               childCount, sessionId, request.getAdjustReason(), request.getAdjustOperator(), records);
        redistributeEquipments(AgeGroup.TEEN, groupedByAge.getOrDefault(AgeGroup.TEEN, new ArrayList<>()), 
                               teenCount, sessionId, request.getAdjustReason(), request.getAdjustOperator(), records);
        redistributeEquipments(AgeGroup.ADULT, groupedByAge.getOrDefault(AgeGroup.ADULT, new ArrayList<>()), 
                               adultCount, sessionId, request.getAdjustReason(), request.getAdjustOperator(), records);
        
        return records;
    }
    
    private void redistributeEquipments(AgeGroup targetGroup, List<SessionEquipment> currentEquipments,
                                        int targetCount, Long sessionId, String reason, String operator,
                                        List<AdjustRecord> records) {
        if (currentEquipments.size() == targetCount) {
            return;
        }
        
        if (currentEquipments.size() > targetCount) {
            int excess = currentEquipments.size() - targetCount;
            List<SessionEquipment> toMove = new ArrayList<>(currentEquipments.subList(0, excess));
            
            for (SessionEquipment se : toMove) {
                AgeGroup oldGroup = se.getTargetAgeGroup();
                se.setTargetAgeGroup(targetGroup);
                sessionEquipmentRepository.save(se);
                
                AdjustRecord record = new AdjustRecord();
                record.setSessionId(sessionId);
                record.setEquipmentId(se.getEquipmentId());
                record.setOldAgeGroup(oldGroup);
                record.setNewAgeGroup(targetGroup);
                record.setAdjustReason(reason);
                record.setAdjustOperator(operator);
                records.add(adjustRecordRepository.save(record));
            }
        }
    }
    
    @Transactional
    public void autoBindByRatio(Long sessionId) {
        Session session = sessionService.getSessionEntity(sessionId);
        
        List<Equipment> availableEquipments = equipmentRepository.findByStatus(EquipmentStatus.AVAILABLE);
        
        Map<AgeGroup, List<Equipment>> availableByAge = availableEquipments.stream()
                .collect(Collectors.groupingBy(Equipment::getAgeGroup));
        
        int totalAvailable = availableEquipments.size();
        int childCount = (int) Math.round(session.getChildRatio().multiply(BigDecimal.valueOf(totalAvailable)).doubleValue());
        int teenCount = (int) Math.round(session.getTeenRatio().multiply(BigDecimal.valueOf(totalAvailable)).doubleValue());
        int adultCount = totalAvailable - childCount - teenCount;
        
        bindEquipments(sessionId, availableByAge.getOrDefault(AgeGroup.CHILD, new ArrayList<>()), 
                       AgeGroup.CHILD, childCount);
        bindEquipments(sessionId, availableByAge.getOrDefault(AgeGroup.TEEN, new ArrayList<>()), 
                       AgeGroup.TEEN, teenCount);
        bindEquipments(sessionId, availableByAge.getOrDefault(AgeGroup.ADULT, new ArrayList<>()), 
                       AgeGroup.ADULT, adultCount);
        
        log.info("场次{}自动绑定完成", sessionId);
    }
    
    private void bindEquipments(Long sessionId, List<Equipment> equipments, AgeGroup targetGroup, int count) {
        int actualCount = Math.min(count, equipments.size());
        for (int i = 0; i < actualCount; i++) {
            Equipment equipment = equipments.get(i);
            
            SessionEquipment sessionEquipment = new SessionEquipment();
            sessionEquipment.setSessionId(sessionId);
            sessionEquipment.setEquipmentId(equipment.getId());
            sessionEquipment.setTargetAgeGroup(targetGroup);
            sessionEquipmentRepository.save(sessionEquipment);
            
            equipment.setStatus(EquipmentStatus.IN_USE);
            equipmentRepository.save(equipment);
        }
    }
    
    private SessionEquipmentDTO convertToDTO(SessionEquipment sessionEquipment) {
        SessionEquipmentDTO dto = new SessionEquipmentDTO();
        dto.setId(sessionEquipment.getId());
        dto.setSessionId(sessionEquipment.getSessionId());
        dto.setEquipmentId(sessionEquipment.getEquipmentId());
        dto.setTargetAgeGroup(sessionEquipment.getTargetAgeGroup().name());
        return dto;
    }
}