package com.icepark.repository;

import com.icepark.entity.AdjustRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdjustRecordRepository extends JpaRepository<AdjustRecord, Long> {
    
    List<AdjustRecord> findBySessionId(Long sessionId);
    
    List<AdjustRecord> findByEquipmentId(Long equipmentId);
    
    List<AdjustRecord> findBySessionIdOrderByAdjustTimeDesc(Long sessionId);
}