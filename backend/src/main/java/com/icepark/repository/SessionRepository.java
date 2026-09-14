package com.icepark.repository;

import com.icepark.entity.Session;
import com.icepark.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {
    
    Optional<Session> findBySessionCode(String sessionCode);
    
    List<Session> findByStatus(SessionStatus status);
    
    boolean existsBySessionCode(String sessionCode);
}