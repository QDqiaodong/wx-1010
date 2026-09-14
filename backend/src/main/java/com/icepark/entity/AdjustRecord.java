package com.icepark.entity;

import com.icepark.enums.AgeGroup;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "adjust_record", indexes = {
    @Index(name = "idx_session_id", columnList = "session_id"),
    @Index(name = "idx_adjust_time", columnList = "adjust_time")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdjustRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_age_group", nullable = false, length = 20)
    private AgeGroup oldAgeGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_age_group", nullable = false, length = 20)
    private AgeGroup newAgeGroup;

    @Column(name = "adjust_reason", length = 500)
    private String adjustReason;

    @Column(name = "adjust_operator", nullable = false, length = 100)
    private String adjustOperator;

    @Column(name = "adjust_time")
    private LocalDateTime adjustTime = LocalDateTime.now();
}