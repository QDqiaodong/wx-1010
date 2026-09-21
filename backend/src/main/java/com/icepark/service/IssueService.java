package com.icepark.service;

import com.icepark.dto.IssueRecordDTO;
import com.icepark.dto.IssueRequestDTO;
import com.icepark.dto.SessionIssueOverviewDTO;
import com.icepark.entity.Equipment;
import com.icepark.entity.IssueRecord;
import com.icepark.entity.Session;
import com.icepark.entity.SessionEquipment;
import com.icepark.enums.AgeGroup;
import com.icepark.enums.IssueStatus;
import com.icepark.enums.SessionStatus;
import com.icepark.exception.ConflictException;
import com.icepark.repository.EquipmentRepository;
import com.icepark.repository.IssueRecordRepository;
import com.icepark.repository.SessionEquipmentRepository;
import com.icepark.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IssueService {

    private final SessionRepository sessionRepository;
    private final SessionEquipmentRepository sessionEquipmentRepository;
    private final EquipmentRepository equipmentRepository;
    private final IssueRecordRepository issueRecordRepository;
    private final FrostLimitResolver frostLimitResolver;

    /**
     * 发装。整体在一个事务内：
     * 1) FOR UPDATE 锁场次行并校验“进行中”；
     * 2) 同时评估年龄段、气温两道匹配（不短路，逐条点名）；
     * 3) 写流水后用带条件的原子 UPDATE 抢占器材，占用失败即 409“已被领用”。
     */
    @Transactional
    public IssueRecordDTO issue(Long sessionId, IssueRequestDTO request) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 锁场次 + 状态时序校验
        Session session;
        try {
            session = sessionRepository.findByIdForUpdate(sessionId)
                    .orElseThrow(() -> new RuntimeException("场次不存在，ID: " + sessionId));
        } catch (PessimisticLockingFailureException e) {
            throw new ConflictException("场次正被结束或被其他发装操作占用，请稍后重试");
        }
        ensureInProgress(session);

        SessionEquipment binding = sessionEquipmentRepository.findById(request.getSessionEquipmentId())
                .orElseThrow(() -> new RuntimeException("该器材未绑定到场次"));
        if (!binding.getSessionId().equals(sessionId)) {
            throw new RuntimeException("该器材不属于当前场次，无法发装");
        }

        Equipment equipment = equipmentRepository.findById(binding.getEquipmentId())
                .orElseThrow(() -> new RuntimeException("器材不存在，ID: " + binding.getEquipmentId()));

        AgeGroup visitorAge;
        try {
            visitorAge = AgeGroup.valueOf(request.getVisitorAgeGroup().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("游客年龄段无效: " + request.getVisitorAgeGroup());
        }

        // 2. 两道匹配同时评估，任一不通过都要明确指出
        validateMatching(binding, equipment, visitorAge, request.getMeasuredTemperature());

        // 3. 先落流水（ISSUED），再以器材行为互斥锚点做原子抢占
        IssueRecord record = new IssueRecord();
        record.setSessionId(sessionId);
        record.setSessionEquipmentId(binding.getId());
        record.setEquipmentId(equipment.getId());
        record.setVisitorId(request.getVisitorId().trim());
        record.setVisitorName(request.getVisitorName());
        record.setVisitorAgeGroup(visitorAge);
        record.setMeasuredTemperature(request.getMeasuredTemperature());
        record.setLimitTemperature(frostLimitResolver.resolve(equipment));
        record.setStatus(IssueStatus.ISSUED);
        record.setIssueOperator(request.getOperator());
        record.setIssueTime(now);
        IssueRecord saved = issueRecordRepository.saveAndFlush(record);

        int claimed = equipmentRepository.claim(equipment.getId(), saved.getId(), now);
        if (claimed == 0) {
            // 唯一能走到这里的原因：并发下已被另一位工作人员领用。抛出后整个事务回滚（流水一并撤销）。
            throw new ConflictException("该器材已被其他游客领用，发装失败");
        }

        log.info("发装成功: 场次{} 器材{} -> 游客{} by {}",
                sessionId, equipment.getEquipmentCode(), saved.getVisitorId(), request.getOperator());
        return toDTO(saved, equipment);
    }

    /**
     * 归还。FOR UPDATE 锁场次后，用条件 UPDATE 把 ISSUED 流水置为 RETURNED（防重复归还），
     * 再仅当占用者仍是本流水时释放器材，释放后器材立即可被再次领用。
     */
    @Transactional
    public IssueRecordDTO returnIssue(Long sessionId, Long issueId, String operator) {
        LocalDateTime now = LocalDateTime.now();

        Session session;
        try {
            session = sessionRepository.findByIdForUpdate(sessionId)
                    .orElseThrow(() -> new RuntimeException("场次不存在，ID: " + sessionId));
        } catch (PessimisticLockingFailureException e) {
            throw new ConflictException("场次正被结束或被其他操作占用，请稍后重试");
        }
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new RuntimeException("场次已结束，未归还器材已由系统在结束时统一兜底，无需单独归还");
        }

        IssueRecord record = issueRecordRepository.findById(issueId)
                .orElseThrow(() -> new RuntimeException("发装流水不存在，ID: " + issueId));
        if (!record.getSessionId().equals(sessionId)) {
            throw new RuntimeException("该发装流水不属于当前场次");
        }
        if (record.getStatus() != IssueStatus.ISSUED) {
            throw new ConflictException(record.getStatus() == IssueStatus.RETURNED
                    ? "该器材已归还，请勿重复归还"
                    : "该器材已随场次结束被系统兜底归还");
        }

        int updated = issueRecordRepository.markReturned(issueId, operator, now);
        if (updated == 0) {
            throw new ConflictException("该器材已被归还或已被场次结束兜底处理");
        }

        equipmentRepository.release(record.getEquipmentId(), issueId, now);

        IssueRecord reloaded = issueRecordRepository.findById(issueId).orElse(record);
        Equipment equipment = equipmentRepository.findById(record.getEquipmentId()).orElse(null);
        log.info("归还成功: 场次{} 流水{} by {}", sessionId, issueId, operator);
        return toDTO(reloaded, equipment);
    }

    /**
     * 场次结束兜底：必须在结束场次的同一事务中调用。
     * 把本场所有 ISSUED 流水原子置为 FORCE_CLOSED，并释放对应器材占用，
     * 保证不会出现“场次已结束却仍挂已领用、既不能绑定也不能归还”的悬空状态。
     *
     * @return 被兜底归还的数量
     */
    @Transactional
    public int forceCloseOpenIssues(Long sessionId, String operator) {
        LocalDateTime now = LocalDateTime.now();
        String reason = "场次结束，系统自动兜底归还";

        List<IssueRecord> open = issueRecordRepository
                .findBySessionIdOrderByIssueTimeDesc(sessionId).stream()
                .filter(r -> r.getStatus() == IssueStatus.ISSUED)
                .collect(Collectors.toList());

        if (open.isEmpty()) {
            return 0;
        }

        int closed = issueRecordRepository.forceCloseBySession(sessionId, operator, now, reason);

        List<Long> issueIds = open.stream().map(IssueRecord::getId).collect(Collectors.toList());
        int released = issueRecordRepository.releaseByIssueIds(issueIds, now);

        log.warn("场次{}结束兜底：兜底归还 {} 件，释放器材占用 {} 件，经办人 {}",
                sessionId, closed, released, operator);
        return closed;
    }

    @Transactional(readOnly = true)
    public long countOpenIssues(Long sessionId) {
        return issueRecordRepository.countBySessionIdAndStatus(sessionId, IssueStatus.ISSUED);
    }

    @Transactional(readOnly = true)
    public List<IssueRecordDTO> getRecordsBySession(Long sessionId) {
        Map<Long, Equipment> equipmentMap = loadEquipmentMap(sessionId);
        return issueRecordRepository.findBySessionIdOrderByIssueTimeDesc(sessionId).stream()
                .map(r -> toDTO(r, equipmentMap.get(r.getEquipmentId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SessionIssueOverviewDTO getOverview(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("场次不存在，ID: " + sessionId));

        List<SessionEquipment> bindings = sessionEquipmentRepository.findBySessionId(sessionId);
        List<Long> equipmentIds = bindings.stream().map(SessionEquipment::getEquipmentId).collect(Collectors.toList());

        Map<Long, Equipment> equipmentMap = equipmentIds.isEmpty()
                ? Collections.emptyMap()
                : equipmentRepository.findAllById(equipmentIds).stream()
                    .collect(Collectors.toMap(Equipment::getId, e -> e));

        // 当前仍在占用的流水，用于展示“在谁手上”
        Map<Long, IssueRecord> openByEquipment = issueRecordRepository
                .findBySessionIdOrderByIssueTimeDesc(sessionId).stream()
                .filter(r -> r.getStatus() == IssueStatus.ISSUED)
                .collect(Collectors.toMap(IssueRecord::getEquipmentId, r -> r, (a, b) -> a));

        List<SessionIssueOverviewDTO.BoundEquipmentItem> items = new ArrayList<>();
        for (SessionEquipment binding : bindings) {
            Equipment e = equipmentMap.get(binding.getEquipmentId());
            SessionIssueOverviewDTO.BoundEquipmentItem item = new SessionIssueOverviewDTO.BoundEquipmentItem();
            item.setSessionEquipmentId(binding.getId());
            item.setEquipmentId(binding.getEquipmentId());
            if (e != null) {
                item.setEquipmentCode(e.getEquipmentCode());
                item.setEquipmentName(e.getName());
                item.setFrostResistanceSpec(e.getFrostResistanceSpec());
                item.setMinTemperature(frostLimitResolver.resolve(e));
                item.setCurrentIssueRecordId(e.getCurrentIssueRecordId());
            }
            item.setTargetAgeGroup(binding.getTargetAgeGroup());
            item.setTargetAgeGroupLabel(binding.getTargetAgeGroup().getLabel());
            IssueRecord open = openByEquipment.get(binding.getEquipmentId());
            if (open != null) {
                item.setCurrentVisitorId(open.getVisitorId());
                item.setCurrentVisitorName(open.getVisitorName());
            }
            items.add(item);
        }

        SessionIssueOverviewDTO overview = new SessionIssueOverviewDTO();
        overview.setSessionId(sessionId);
        overview.setSessionCode(session.getSessionCode());
        overview.setSessionName(session.getSessionName());
        overview.setSessionStatus(session.getStatus().name());
        overview.setSessionStatusLabel(session.getStatus().getLabel());
        overview.setItems(items);
        overview.setRecords(getRecordsBySession(sessionId));
        return overview;
    }

    // ---------------- 内部方法 ----------------

    private void ensureInProgress(Session session) {
        if (session.getStatus() == SessionStatus.SCHEDULED) {
            throw new RuntimeException("场次尚未开始，当前为“已安排”状态，不允许发装");
        }
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new RuntimeException("场次已结束，不允许发装");
        }
    }

    /**
     * 年龄段 + 气温双校验，两条独立评估、错误信息合并，一次性说清楚是哪条不匹配。
     */
    private void validateMatching(SessionEquipment binding, Equipment equipment,
                                  AgeGroup visitorAge, BigDecimal measured) {
        List<String> mismatches = new ArrayList<>();

        // 规则一：游客年龄段必须落在该器材目标年龄段允许范围内（本系统目标年龄段为确定的单一档，直接相等）
        if (!isAgeAllowed(binding.getTargetAgeGroup(), visitorAge)) {
            mismatches.add(String.format("年龄段不匹配：该器材面向[%s]，游客为[%s]",
                    binding.getTargetAgeGroup().getLabel(), visitorAge.getLabel()));
        }

        // 规则二：实测气温不得低于器材抗冻下限（临界值等于下限视为可用）
        BigDecimal limit = frostLimitResolver.resolve(equipment);
        if (limit == null) {
            mismatches.add(String.format("气温不匹配：器材[%s]缺少可判定的抗冻下限温度，无法保证安全，禁止发装",
                    equipment.getEquipmentCode()));
        } else if (measured.compareTo(limit) < 0) {
            mismatches.add(String.format("气温不匹配：现场实测 %.1f℃ 低于器材抗冻下限 %.1f℃",
                    measured, limit));
        }

        if (!mismatches.isEmpty()) {
            throw new RuntimeException(String.join("；", mismatches));
        }
    }

    private boolean isAgeAllowed(AgeGroup target, AgeGroup visitor) {
        return target == visitor;
    }

    private Map<Long, Equipment> loadEquipmentMap(Long sessionId) {
        List<Long> ids = sessionEquipmentRepository.findBySessionId(sessionId).stream()
                .map(SessionEquipment::getEquipmentId)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return equipmentRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Equipment::getId, e -> e));
    }

    private IssueRecordDTO toDTO(IssueRecord r, Equipment equipment) {
        IssueRecordDTO dto = new IssueRecordDTO();
        dto.setId(r.getId());
        dto.setSessionId(r.getSessionId());
        dto.setSessionEquipmentId(r.getSessionEquipmentId());
        dto.setEquipmentId(r.getEquipmentId());
        dto.setLimitTemperature(r.getLimitTemperature());
        dto.setVisitorId(r.getVisitorId());
        dto.setVisitorName(r.getVisitorName());
        dto.setVisitorAgeGroup(r.getVisitorAgeGroup());
        dto.setVisitorAgeGroupLabel(r.getVisitorAgeGroup() != null ? r.getVisitorAgeGroup().getLabel() : null);
        dto.setMeasuredTemperature(r.getMeasuredTemperature());
        dto.setStatus(r.getStatus());
        dto.setStatusLabel(r.getStatus() != null ? r.getStatus().getLabel() : null);
        dto.setIssueOperator(r.getIssueOperator());
        dto.setIssueTime(r.getIssueTime());
        dto.setReturnOperator(r.getReturnOperator());
        dto.setReturnTime(r.getReturnTime());
        dto.setCloseReason(r.getCloseReason());
        if (equipment != null) {
            dto.setEquipmentCode(equipment.getEquipmentCode());
            dto.setEquipmentName(equipment.getName());
            dto.setFrostResistanceSpec(equipment.getFrostResistanceSpec());
        }
        return dto;
    }
}
