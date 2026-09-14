package com.icepark.entity;

import com.icepark.enums.AgeGroup;
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

    @Column(name = "bind_time")
    private LocalDateTime bindTime = LocalDateTime.now();
}