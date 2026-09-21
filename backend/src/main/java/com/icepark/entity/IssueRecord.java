package com.icepark.entity;

import com.icepark.enums.AgeGroup;
import com.icepark.enums.IssueStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 入场发装流水：一条记录对应一次“发出去 -> 用着 -> 还回来”的完整闭环。
 * status=ISSUED 表示仍在游客手中；RETURNED 为正常归还；FORCE_CLOSED 为场次结束时的系统兜底归还。
 */
@Entity
@Table(name = "issue_record", indexes = {
    @Index(name = "idx_ir_session", columnList = "session_id"),
    @Index(name = "idx_ir_equipment", columnList = "equipment_id"),
    @Index(name = "idx_ir_status", columnList = "status"),
    @Index(name = "idx_ir_issue_time", columnList = "issue_time")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IssueRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 对应 session_equipment 绑定行，记录本次发装针对的是场次里的哪一件绑定器材 */
    @Column(name = "session_equipment_id", nullable = false)
    private Long sessionEquipmentId;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    /** 游客凭证号（票号/手环号等） */
    @Column(name = "visitor_id", nullable = false, length = 64)
    private String visitorId;

    @Column(name = "visitor_name", length = 100)
    private String visitorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "visitor_age_group", nullable = false, length = 20)
    private AgeGroup visitorAgeGroup;

    /** 发装时现场实测气温（℃） */
    @Column(name = "measured_temperature", nullable = false, precision = 5, scale = 2)
    private BigDecimal measuredTemperature;

    /** 发装时该器材生效的抗冻下限（℃），随单固化，便于事后追溯 */
    @Column(name = "limit_temperature", nullable = false, precision = 5, scale = 2)
    private BigDecimal limitTemperature;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IssueStatus status = IssueStatus.ISSUED;

    @Column(name = "issue_operator", nullable = false, length = 100)
    private String issueOperator;

    @Column(name = "issue_time", nullable = false)
    private LocalDateTime issueTime;

    /** 正常归还时的经办人；兜底归还时记录结束场次的经办人 */
    @Column(name = "return_operator", length = 100)
    private String returnOperator;

    @Column(name = "return_time")
    private LocalDateTime returnTime;

    /** 非主动归还时的原因（如场次结束系统兜底） */
    @Column(name = "close_reason", length = 255)
    private String closeReason;
}
