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
     * 发装/归还/结束场次都先对场次行加悲观写锁，把同一场次的状态变更串行化：
     * 既解决同一器材的并发领用，也杜绝"发装进行中、场次恰好被结束"的竞态。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Session s WHERE s.id = :id")
    Optional<Session> findByIdForUpdate(@Param("id") Long id);
}