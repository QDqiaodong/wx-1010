package com.icepark.entity;

import com.icepark.enums.AgeGroup;
import com.icepark.enums.EquipmentStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "equipment", indexes = {
    @Index(name = "idx_age_group", columnList = "age_group"),
    @Index(name = "idx_category", columnList = "category")
}, uniqueConstraints = {
    // 发装占用锚点：非空即表示该器材正被某条流水占用；唯一约束从存储层杜绝双重占用
    @UniqueConstraint(name = "uk_equipment_current_issue", columnNames = "current_issue_record_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_code", nullable = false, unique = true, length = 50)
    private String equipmentCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "frost_resistance_spec", nullable = false, length = 200)
    private String frostResistanceSpec;

    /**
     * 抗冻下限（℃），实测气温低于该值不允许发装。
     * 允许为空以兼容历史数据：为空时回退为从 frostResistanceSpec 文本中解析。
     */
    @Column(name = "min_temperature", precision = 5, scale = 2)
    private java.math.BigDecimal minTemperature;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", nullable = false, length = 20)
    private AgeGroup ageGroup;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private EquipmentStatus status = EquipmentStatus.AVAILABLE;

    /**
     * 当前占用该器材的发装流水 ID。null = 在库可领；非 null = 已发给某位游客。
     * 发装通过一条带条件的原子 UPDATE 抢占该列，是并发互斥的核心，而非“先读状态再写”。
     */
    @Column(name = "current_issue_record_id")
    private Long currentIssueRecordId;

    @Column(name = "create_time")
    private LocalDateTime createTime = LocalDateTime.now();

    @Column(name = "update_time")
    private LocalDateTime updateTime = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}