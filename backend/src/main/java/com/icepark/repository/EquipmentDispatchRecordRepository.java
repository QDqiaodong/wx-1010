package com.icepark.repository;

import com.icepark.entity.EquipmentDispatchRecord;
import com.icepark.enums.DispatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EquipmentDispatchRecordRepository extends JpaRepository<EquipmentDispatchRecord, Long> {

    /** 按场次查询完整发装流水（发装时间倒序） */
    List<EquipmentDispatchRecord> findBySessionIdOrderByIssueTimeDescIdDesc(Long sessionId);

    /** 场次结束兜底：查出该场次所有发出未归还的流水 */
    List<EquipmentDispatchRecord> findBySessionIdAndStatus(Long sessionId, DispatchStatus status);

    boolean existsBySessionIdAndStatus(Long sessionId, DispatchStatus status);

    boolean existsByEquipmentIdAndStatus(Long equipmentId, DispatchStatus status);

    boolean existsByOutstandingKey(String outstandingKey);

    /**
     * 归还/兜底收回：只允许把仍是 ISSUED 的记录置为终态，
     * 条件更新保证状态机单向，返回 0 说明已被处理过。
     * clearAutomatically：批量 UPDATE 不经过一级缓存，执行后清空持久化上下文，
     * 避免同事务内随后 findById 读到旧状态。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EquipmentDispatchRecord r SET r.status = :toStatus, " +
           "r.returnOperator = :returnOperator, r.returnTime = :returnTime, r.outstandingKey = NULL " +
           "WHERE r.id = :id AND r.status = com.icepark.enums.DispatchStatus.ISSUED")
    int closeRecord(@Param("id") Long id,
                    @Param("toStatus") DispatchStatus toStatus,
                    @Param("returnOperator") String returnOperator,
                    @Param("returnTime") java.time.LocalDateTime returnTime);

    /** 归还完成后重新读取最新状态（关闭操作走的是批量 UPDATE） */
    Optional<EquipmentDispatchRecord> findByIdAndSessionId(Long id, Long sessionId);
}
