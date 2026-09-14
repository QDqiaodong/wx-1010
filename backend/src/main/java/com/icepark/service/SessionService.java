package com.icepark.service;

import com.icepark.dto.SessionDTO;
import com.icepark.entity.Session;
import com.icepark.enums.AgeGroup;
import com.icepark.enums.SessionStatus;
import com.icepark.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {
    
    private final SessionRepository sessionRepository;
    
    public List<SessionDTO> getAllSessions() {
        return sessionRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    public SessionDTO getSessionById(Long id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("场次不存在，ID: " + id));
        return convertToDTO(session);
    }
    
    @Transactional
    public SessionDTO createSession(SessionDTO dto) {
        validateRatio(dto.getChildRatio(), dto.getTeenRatio(), dto.getAdultRatio());
        
        if (sessionRepository.existsBySessionCode(dto.getSessionCode())) {
            throw new RuntimeException("场次编号已存在: " + dto.getSessionCode());
        }
        
        Session session = new Session();
        session.setSessionCode(dto.getSessionCode());
        session.setSessionName(dto.getSessionName());
        session.setStartTime(dto.getStartTime());
        session.setEndTime(dto.getEndTime());
        session.setChildRatio(dto.getChildRatio());
        session.setTeenRatio(dto.getTeenRatio());
        session.setAdultRatio(dto.getAdultRatio());
        session.setStatus(dto.getStatus() != null ? 
                SessionStatus.valueOf(dto.getStatus().toUpperCase()) : SessionStatus.SCHEDULED);
        
        Session saved = sessionRepository.save(session);
        return convertToDTO(saved);
    }
    
    @Transactional
    public SessionDTO updateSession(Long id, SessionDTO dto) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("场次不存在，ID: " + id));
        
        if (!session.getSessionCode().equals(dto.getSessionCode()) &&
                sessionRepository.existsBySessionCode(dto.getSessionCode())) {
            throw new RuntimeException("场次编号已存在: " + dto.getSessionCode());
        }
        
        validateRatio(dto.getChildRatio(), dto.getTeenRatio(), dto.getAdultRatio());
        
        session.setSessionCode(dto.getSessionCode());
        session.setSessionName(dto.getSessionName());
        session.setStartTime(dto.getStartTime());
        session.setEndTime(dto.getEndTime());
        session.setChildRatio(dto.getChildRatio());
        session.setTeenRatio(dto.getTeenRatio());
        session.setAdultRatio(dto.getAdultRatio());
        if (dto.getStatus() != null) {
            session.setStatus(SessionStatus.valueOf(dto.getStatus().toUpperCase()));
        }
        
        Session saved = sessionRepository.save(session);
        return convertToDTO(saved);
    }
    
    @Transactional
    public void deleteSession(Long id) {
        if (!sessionRepository.existsById(id)) {
            throw new RuntimeException("场次不存在，ID: " + id);
        }
        sessionRepository.deleteById(id);
    }
    
    private void validateRatio(BigDecimal child, BigDecimal teen, BigDecimal adult) {
        if (child == null || teen == null || adult == null) {
            throw new RuntimeException("客群占比不能为空");
        }
        BigDecimal sum = child.add(teen).add(adult);
        if (sum.compareTo(BigDecimal.ONE) != 0) {
            throw new RuntimeException("客群占比总和必须等于1");
        }
    }
    
    private SessionDTO convertToDTO(Session session) {
        SessionDTO dto = new SessionDTO();
        dto.setId(session.getId());
        dto.setSessionCode(session.getSessionCode());
        dto.setSessionName(session.getSessionName());
        dto.setStartTime(session.getStartTime());
        dto.setEndTime(session.getEndTime());
        dto.setChildRatio(session.getChildRatio());
        dto.setTeenRatio(session.getTeenRatio());
        dto.setAdultRatio(session.getAdultRatio());
        dto.setStatus(session.getStatus().name());
        return dto;
    }
    
    public Session getSessionEntity(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("场次不存在，ID: " + id));
    }
}