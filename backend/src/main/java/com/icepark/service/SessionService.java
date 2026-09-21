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
    private final IssueService issueService;

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
        // 新建场次一律为“已安排”，状态流转只能通过开始/结束接口，确保结束兜底不被绕过
        session.setStatus(SessionStatus.SCHEDULED);

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
        // 编辑基础信息不改变场次状态：开始/结束必须走专门接口，避免绕过结束兜底
        // （session.status 维持原值）

        Session saved = sessionRepository.save(session);
        return convertToDTO(saved);
    }

    /** 开始场次：仅“已安排”可开始 */
    @Transactional
    public SessionDTO startSession(Long id) {
        Session session = sessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("场次不存在，ID: " + id));
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new RuntimeException("只有“已安排”的场次才能开始，当前状态：" + session.getStatus().getLabel());
        }
        session.setStatus(SessionStatus.IN_PROGRESS);
        return convertToDTO(sessionRepository.save(session));
    }

    /**
     * 结束场次：仅“进行中”可结束。在同一事务内先兜底归还所有未归还器材，再置为已结束。
     * 原子性保证：要么兜底+结束都成功，要么都不发生，绝不留下悬空“已领用”。
     */
    @Transactional
    public SessionDTO endSession(Long id, String operator) {
        Session session = sessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("场次不存在，ID: " + id));
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new RuntimeException("场次已结束，请勿重复结束");
        }
        if (session.getStatus() == SessionStatus.SCHEDULED) {
            throw new RuntimeException("场次尚未开始，不能结束");
        }

        String op = (operator == null || operator.isBlank()) ? "system" : operator;
        int forced = issueService.forceCloseOpenIssues(id, op);

        session.setStatus(SessionStatus.ENDED);
        Session saved = sessionRepository.save(session);
        log.info("场次{}结束，兜底归还未归还器材 {} 件，经办人 {}", id, forced, op);
        return convertToDTO(saved);
    }
    
    @Transactional
    public void deleteSession(Long id) {
        if (!sessionRepository.existsById(id)) {
            throw new RuntimeException("场次不存在，ID: " + id);
        }
        if (issueService.countOpenIssues(id) > 0) {
            throw new RuntimeException("该场次仍有器材在游客手中，请先结束场次（系统会兜底归还）后再删除");
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