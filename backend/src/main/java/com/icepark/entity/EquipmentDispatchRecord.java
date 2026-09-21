package com.icepark.entity;

import com.icepark.enums.AgeGroup;
import com.icepark.enums.DispatchStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 现场发装/归还流水。一条记录 = 一次"发给某位游客 + 后来归还/兜底收回"的完整闭环。
 *
 * 并发安全的数据库兜底：outstandingKey 在未归还期间写器材ID（issued:{equipmentId}），
 * 归还后置为 NULL，并配唯一索引。同一件器材任何时刻只允许存在一条未归还流水，
 * 即使两个请求同时穿过应用层，数据库也只可能放行一个 INSERT。
 */
@Entity
@Table(name = "equipment_dispatch_record",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dispatch_outstanding",
                columnNames = "outstanding_key"),
        indexes = {
                @Index(name = "idx_dispatch_session_id", columnList = "session_id"),
                @Index(name = "idx_dispatch_equipment_id", columnList = "equipment_id"),
                @Index(name = "idx_dispatch_status_id", columnList = "status")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentDispatchRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "session_equipment_id", nullable = false)
    private Long sessionEquipmentId;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    /** 领装游客姓名/编号（现场登记，用于流水追溯） */
    @Column(name = "visitor_name", nullable = false, length = 100)
    private String visitorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "visitor_age_group", nullable = false, length = 20)
    private AgeGroup visitorAgeGroup;

    /** 发装时的实测气温（摄氏度），留痕便于事后核查临界气温发装 */
    @Column(name = "temperature_at_issue", nullable = false, precision = 5, scale = 2)
    private BigDecimal temperatureAtIssue;

    /** 发装时该器材抗冻规格可承受的下限气温，留痕 */
    @Column(name = "frost_lower_limit_at_issue", nullable = false, precision = 5, scale = 2)
    private BigDecimal frostLowerLimitAtIssue;

    /** 发装操作员 */
    @Column(name = "issue_operator", nullable = false, length = 100)
    private String issueOperator;

    @Column(name = "issue_time", nullable = false)
    private LocalDateTime issueTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DispatchStatus status = DispatchStatus.ISSUED;

    /** 归还操作员（游客正常归还时有值） */
    @Column(name = "return_operator", length = 100)
    private String returnOperator;

    @Column(name = "return_time")
    private LocalDateTime returnTime;

    /** 未归还期间为 "issued:{equipmentId}"，归还/兜底收回后为 NULL。配合唯一索引实现并发互斥。 */
    @Column(name = "outstanding_key", length = 64)
    private String outstandingKey;
}
