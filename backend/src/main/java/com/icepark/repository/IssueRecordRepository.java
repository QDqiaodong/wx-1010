package com.icepark.repository;

import com.icepark.entity.IssueRecord;
import com.icepark.enums.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IssueRecordRepository extends JpaRepository<IssueRecord, Long> {

    List<IssueRecord> findBySessionIdOrderByIssueTimeDesc(Long sessionId);

    Optional<IssueRecord> findFirstByEquipmentIdAndStatusOrderByIssueTimeDesc(Long equipmentId, IssueStatus status);

    /** 归还：只有仍是 ISSUED 的流水才能被置为 RETURNED，作为“防重复归还”的闸门 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE IssueRecord r SET r.status = com.icepark.enums.IssueStatus.RETURNED, " +
           "r.returnOperator = :operator, r.returnTime = :time, r.closeReason = NULL " +
           "WHERE r.id = :id AND r.status = com.icepark.enums.IssueStatus.ISSUED")
    int markReturned(@Param("id") Long id,
                     @Param("operator") String operator,
                     @Param("time") LocalDateTime time);

    /** 场次结束兜底：把本场所有未归还流水批量置为兜底归还 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE IssueRecord r SET r.status = com.icepark.enums.IssueStatus.FORCE_CLOSED, " +
           "r.returnOperator = :operator, r.returnTime = :time, r.closeReason = :reason " +
           "WHERE r.sessionId = :sessionId AND r.status = com.icepark.enums.IssueStatus.ISSUED")
    int forceCloseBySession(@Param("sessionId") Long sessionId,
                            @Param("operator") String operator,
                            @Param("time") LocalDateTime time,
                            @Param("reason") String reason);

    /** 仅释放被兜底流水占用的器材，WHERE 带 currentIssueRecordId 防止误释放 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Equipment e SET e.currentIssueRecordId = NULL, e.status = com.icepark.enums.EquipmentStatus.AVAILABLE, " +
           "e.updateTime = :now WHERE e.currentIssueRecordId IN :issueIds")
    int releaseByIssueIds(@Param("issueIds") List<Long> issueIds,
                          @Param("now") LocalDateTime now);

    long countBySessionIdAndStatus(Long sessionId, IssueStatus status);
}
