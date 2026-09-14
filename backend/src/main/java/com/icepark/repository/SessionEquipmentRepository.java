package com.icepark.repository;

import com.icepark.entity.SessionEquipment;
import com.icepark.enums.AgeGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionEquipmentRepository extends JpaRepository<SessionEquipment, Long> {
    
    List<SessionEquipment> findBySessionId(Long sessionId);
    
    List<SessionEquipment> findByEquipmentId(Long equipmentId);
    
    List<SessionEquipment> findBySessionIdAndTargetAgeGroup(Long sessionId, AgeGroup ageGroup);
    
    void deleteBySessionId(Long sessionId);
    
    void deleteBySessionIdAndEquipmentId(Long sessionId, Long equipmentId);
    
    boolean existsBySessionIdAndEquipmentId(Long sessionId, Long equipmentId);
}