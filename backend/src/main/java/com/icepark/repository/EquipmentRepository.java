package com.icepark.repository;

import com.icepark.entity.Equipment;
import com.icepark.enums.AgeGroup;
import com.icepark.enums.EquipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    
    Optional<Equipment> findByEquipmentCode(String equipmentCode);
    
    List<Equipment> findByAgeGroup(AgeGroup ageGroup);
    
    List<Equipment> findByCategory(String category);
    
    List<Equipment> findByStatus(EquipmentStatus status);
    
    List<Equipment> findByAgeGroupAndStatus(AgeGroup ageGroup, EquipmentStatus status);
    
    List<Equipment> findByAgeGroupIn(List<AgeGroup> ageGroups);
    
    boolean existsByEquipmentCode(String equipmentCode);
}