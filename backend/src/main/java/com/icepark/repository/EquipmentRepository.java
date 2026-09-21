package com.icepark.repository;

import com.icepark.entity.Equipment;
import com.icepark.enums.AgeGroup;
import com.icepark.enums.EquipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    /**
     * 原子抢占：仅当器材当前未被占用时，才把占用锚点置为本次流水。
     * 依赖数据库行锁保证并发下最多一条 UPDATE 命中，返回受影响行数 1 即抢占成功。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Equipment e SET e.currentIssueRecordId = :issueId, e.status = com.icepark.enums.EquipmentStatus.IN_USE, " +
           "e.updateTime = :now WHERE e.id = :equipmentId AND e.currentIssueRecordId IS NULL")
    int claim(@Param("equipmentId") Long equipmentId,
              @Param("issueId") Long issueId,
              @Param("now") LocalDateTime now);

    /**
     * 原子释放：只有占用者仍是本流水时才清空，避免并发下误释放别人的占用。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Equipment e SET e.currentIssueRecordId = NULL, e.status = com.icepark.enums.EquipmentStatus.AVAILABLE, " +
           "e.updateTime = :now WHERE e.id = :equipmentId AND e.currentIssueRecordId = :issueId")
    int release(@Param("equipmentId") Long equipmentId,
                @Param("issueId") Long issueId,
                @Param("now") LocalDateTime now);
}