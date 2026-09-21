package com.icepark.repository;

import com.icepark.entity.Session;
import com.icepark.enums.SessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findBySessionCode(String sessionCode);

    List<Session> findByStatus(SessionStatus status);

    boolean existsBySessionCode(String sessionCode);

    /**
     * 悲观行锁：发装/归还/结束在同一事务内先锁场次行，串行化对同一场次的状态判定，
     * 杜绝“发装判定进行中”与“场次结束兜底”并发交错造成的悬空占用。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Session s WHERE s.id = :id")
    Optional<Session> findByIdForUpdate(@Param("id") Long id);
}