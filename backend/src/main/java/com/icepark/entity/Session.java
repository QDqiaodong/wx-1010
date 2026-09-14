package com.icepark.entity;

import com.icepark.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "session", indexes = {
    @Index(name = "idx_session_code", columnList = "session_code"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_code", nullable = false, unique = true, length = 50)
    private String sessionCode;

    @Column(name = "session_name", nullable = false, length = 100)
    private String sessionName;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "child_ratio", nullable = false, precision = 5, scale = 2)
    private BigDecimal childRatio;

    @Column(name = "teen_ratio", nullable = false, precision = 5, scale = 2)
    private BigDecimal teenRatio;

    @Column(name = "adult_ratio", nullable = false, precision = 5, scale = 2)
    private BigDecimal adultRatio;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private SessionStatus status = SessionStatus.SCHEDULED;

    @Column(name = "create_time")
    private LocalDateTime createTime = LocalDateTime.now();

    @Column(name = "update_time")
    private LocalDateTime updateTime = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}