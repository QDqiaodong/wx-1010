package com.icepark.service;

import com.icepark.dto.SessionDTO;
import com.icepark.entity.Session;
import com.icepark.enums.SessionStatus;
import com.icepark.exception.BusinessValidationException;
import com.icepark.exception.ConflictException;
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
    private final EquipmentDispatchService equipmentDispatchService;

    public List<SessionDTO> getAllSessions() {
        return sessionRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public SessionDTO getSessionById(Long id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new BusinessValidationException("场次不存在，ID: " + id));
        return convertToDTO(session);
    }

    @Transactional
    public SessionDTO createSession(SessionDTO dto) {
        validateRatio(dto.getChildRatio(), dto.getTeenRatio(), dto.getAdultRatio());

        if (sessionRepository.existsBySessionCode(dto.getSessionCode())) {
            throw new BusinessValidationException("场次编号已存在: " + dto.getSessionCode());
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
                parseStatus(dto.getStatus()) : SessionStatus.SCHEDULED);

        Session saved = sessionRepository.save(session);
        return convertToDTO(saved);
    }

    @Transactional
    public SessionDTO updateSession(Long id, SessionDTO dto) {
        Session session = sessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessValidationException("场次不存在，ID: " + id));

        if (!session.getSessionCode().equals(dto.getSessionCode()) &&
                sessionRepository.existsBySessionCode(dto.getSessionCode())) {
            throw new BusinessValidationException("场次编号已存在: " + dto.getSessionCode());
        }

        validateRatio(dto.getChildRatio(), dto.getTeenRatio(), dto.getAdultRatio());

        SessionStatus newStatus = dto.getStatus() != null
                ? parseStatus(dto.getStatus()) : session.getStatus();

        session.setSessionCode(dto.getSessionCode());
        session.setSessionName(dto.getSessionName());
        session.setStartTime(dto.getStartTime());
        session.setEndTime(dto.getEndTime());
        session.setChildRatio(dto.getChildRatio());
        session.setTeenRatio(dto.getTeenRatio());
        session.setAdultRatio(dto.getAdultRatio());
        session.setStatus(newStatus);

        // 任何把场次置为"已结束"的入口都必须走兜底逻辑（编辑表单直接改状态同样生效）
        if (newStatus == SessionStatus.ENDED) {
            int forced = equipmentDispatchService.forceCloseOutstanding(session, null);
            log.info("场次{}通过编辑结束，兜底收回未归还器材{}件", id, forced);
        }

        Session saved = sessionRepository.save(session);
        return convertToDTO(saved);
    }

    /** 场次开始：已安排 -> 进行中 */
    @Transactional
    public SessionDTO startSession(Long id) {
        Session session = sessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessValidationException("场次不存在，ID: " + id));
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new BusinessValidationException("场次已结束，不能重新开始");
        }
        if (session.getStatus() == SessionStatus.IN_PROGRESS) {
            throw new BusinessValidationException("场次已在进行中");
        }
        session.setStatus(SessionStatus.IN_PROGRESS);
        return convertToDTO(sessionRepository.save(session));
    }

    /**
     * 场次结束（现场专用入口）：进行中 -> 已结束，
     * 同一事务内对所有发出未归还器材做兜底收回，杜绝悬空状态。
     */
    @Transactional
    public SessionDTO endSession(Long id) {
        Session session = sessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessValidationException("场次不存在，ID: " + id));
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new BusinessValidationException("场次已结束，无需重复操作");
        }
        int forced = equipmentDispatchService.forceCloseOutstanding(session, null);
        session.setStatus(SessionStatus.ENDED);
        Session saved = sessionRepository.save(session);
        log.info("场次{}结束，兜底收回未归还器材{}件", id, forced);
        return convertToDTO(saved);
    }

    @Transactional
    public void deleteSession(Long id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new BusinessValidationException("场次不存在，ID: " + id));
        if (equipmentDispatchService.sessionHasOutstanding(id)) {
            throw new ConflictException("场次仍有已领用未归还的器材，请先结束场次完成兜底收回后再删除");
        }
        sessionRepository.delete(session);
    }

    private void validateRatio(BigDecimal child, BigDecimal teen, BigDecimal adult) {
        if (child == null || teen == null || adult == null) {
            throw new BusinessValidationException("客群占比不能为空");
        }
        BigDecimal sum = child.add(teen).add(adult);
        if (sum.compareTo(BigDecimal.ONE) != 0) {
            throw new BusinessValidationException("客群占比总和必须等于1");
        }
    }

    private SessionStatus parseStatus(String raw) {
        try {
            return SessionStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessValidationException("场次状态不合法：" + raw);
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
                .orElseThrow(() -> new BusinessValidationException("场次不存在，ID: " + id));
    }
}
