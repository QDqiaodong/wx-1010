package com.icepark.entity;

import com.icepark.enums.AgeGroup;
import com.icepark.enums.BindDispatchStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "session_equipment", indexes = {
    @Index(name = "idx_session_id", columnList = "session_id"),
    @Index(name = "idx_equipment_id", columnList = "equipment_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionEquipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_age_group", nullable = false, length = 20)
    private AgeGroup targetAgeGroup;

    /**
     * 现场发装状态：AVAILABLE 在架 / ISSUED 已发给游客未归还。
     * 与 equipment.status（资产级状态，绑定即 IN_USE）区分开。
     * 不设 nullable=false + 表级 default，避免历史数据 ALTER 后出现非空映射异常。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "dispatch_status", length = 20)
    private BindDispatchStatus dispatchStatus = BindDispatchStatus.AVAILABLE;

    @Column(name = "bind_time")
    private LocalDateTime bindTime = LocalDateTime.now();
}