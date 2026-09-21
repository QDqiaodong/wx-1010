package com.icepark.service;

import com.icepark.dto.EquipmentDispatchRecordDTO;
import com.icepark.dto.IssueRequestDTO;
import com.icepark.dto.SessionDispatchItemDTO;
import com.icepark.entity.Equipment;
import com.icepark.entity.EquipmentDispatchRecord;
import com.icepark.entity.Session;
import com.icepark.entity.SessionEquipment;
import com.icepark.enums.AgeGroup;
import com.icepark.enums.BindDispatchStatus;
import com.icepark.enums.DispatchStatus;
import com.icepark.enums.EquipmentStatus;
import com.icepark.enums.SessionStatus;
import com.icepark.exception.BusinessValidationException;
import com.icepark.exception.ConflictException;
import com.icepark.repository.EquipmentDispatchRecordRepository;
import com.icepark.repository.EquipmentRepository;
import com.icepark.repository.SessionEquipmentRepository;
import com.icepark.repository.SessionRepository;
import com.icepark.util.AgeGroupMatcher;
import com.icepark.util.FrostSpecParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EquipmentDispatchService {

    /** 未归还流水的互斥键前缀，配合 dispatch_record 表唯一索引使用 */
    private static final String OUTSTANDING_KEY_PREFIX = "issued:";
    private static final String FALLBACK_OPERATOR = "系统兜底收回";

    private final SessionRepository sessionRepository;
    private final SessionEquipmentRepository sessionEquipmentRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentDispatchRecordRepository dispatchRecordRepository;

    /**
     * 发装：把某一件具体器材发给某一位游客。
     *
     * 并发保证（三道，均在后端/数据库，不依赖前端）：
     * 1) 先对场次行加悲观写锁，同一场次的所有发装/归还/结束串行化，顺带消除"发装中途场次被结束"竞态；
     * 2) 绑定状态做条件 UPDATE（CAS：仅在架可置已领用），影响行数 0 即判定已被领用；
     * 3) 流水表 outstanding_key 唯一索引兜底跨场次/异常路径，同一件器材任何时刻至多一条未归还流水。
     */
    @Transactional
    public EquipmentDispatchRecordDTO issue(Long sessionId, IssueRequestDTO request) {
        Session session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessValidationException("场次不存在，ID: " + sessionId));

        // 约束三：只有进行中的场次允许发装
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new BusinessValidationException(
                    "场次当前状态为「" + session.getStatus().getLabel() + "」，只有「进行中」的场次才允许发装");
        }

        SessionEquipment binding = sessionEquipmentRepository
                .findBySessionIdAndEquipmentId(sessionId, request.getEquipmentId())
                .orElseThrow(() -> new BusinessValidationException(
                        "该器材未绑定到本场次，无法发装（equipmentId=" + request.getEquipmentId() + "）"));

        Equipment equipment = equipmentRepository.findById(request.getEquipmentId())
                .orElseThrow(() -> new BusinessValidationException("器材不存在，ID: " + request.getEquipmentId()));

        AgeGroup visitorAgeGroup = parseAgeGroup(request.getVisitorAgeGroup());

        // 约束二：年龄段、气温两条校验同时执行，任意不满足都要说明是哪一条
        BigDecimal frostLowerLimit = FrostSpecParser.parseLowerLimit(equipment.getFrostResistanceSpec());
        List<String> mismatches = new ArrayList<>();
        if (!AgeGroupMatcher.isAllowed(visitorAgeGroup, binding.getTargetAgeGroup())) {
            mismatches.add(String.format(
                    "年龄段不匹配：游客为「%s」，该器材在本场次的目标年龄段为「%s」，不在允许范围内",
                    visitorAgeGroup.getLabel(), binding.getTargetAgeGroup().getLabel()));
        }
        if (request.getTemperature().compareTo(frostLowerLimit) < 0) {
            mismatches.add(String.format(
                    "气温不匹配：现场实测 %s℃ 低于该器材抗冻规格下限 %s℃（%s），禁止发装",
                    request.getTemperature().stripTrailingZeros().toPlainString(),
                    frostLowerLimit.stripTrailingZeros().toPlainString(),
                    equipment.getFrostResistanceSpec()));
        }
        if (!mismatches.isEmpty()) {
            throw new BusinessValidationException(String.join("；", mismatches));
        }

        // 约束一：CAS 抢占，同一器材并发领用只有一个 UPDATE 能影响到行
        int updated = sessionEquipmentRepository.markIssuedIfAvailable(
                binding.getId(), BindDispatchStatus.ISSUED, BindDispatchStatus.AVAILABLE);
        if (updated == 0) {
            throw new ConflictException("器材「" + equipment.getEquipmentCode() + " " + equipment.getName()
                    + "」已被其他工作人员领用，本次发装失败");
        }

        LocalDateTime now = LocalDateTime.now();
        EquipmentDispatchRecord record = new EquipmentDispatchRecord();
        record.setSessionId(sessionId);
        record.setSessionEquipmentId(binding.getId());
        record.setEquipmentId(equipment.getId());
        record.setVisitorName(request.getVisitorName());
        record.setVisitorAgeGroup(visitorAgeGroup);
        record.setTemperatureAtIssue(request.getTemperature());
        record.setFrostLowerLimitAtIssue(frostLowerLimit);
        record.setIssueOperator(request.getOperator());
        record.setIssueTime(now);
        record.setStatus(DispatchStatus.ISSUED);
        record.setOutstandingKey(outstandingKey(equipment.getId()));

        try {
            record = dispatchRecordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            // 唯一索引兜底：理论上 CAS 已挡住；走到这里说明存在跨场次并发领用同一器材
            log.warn("发装命中未归还流水唯一约束，sessionId={}, equipmentId={}", sessionId, equipment.getId(), e);
            throw new ConflictException("器材「" + equipment.getEquipmentCode() + " " + equipment.getName()
                    + "」已在其他场次被领用且未归还，本次发装失败");
        }

        log.info("发装成功: 场次{} 器材{} 游客{}({}) 气温{} 操作员{}",
                sessionId, equipment.getId(), request.getVisitorName(), visitorAgeGroup,
                request.getTemperature(), request.getOperator());

        return toRecordDTO(record, equipment);
    }

    /**
     * 归还：游客离场归还器材。条件更新保证幂等语义，归还后器材立刻可重新领用。
     * 加锁顺序与发装/结束完全一致（先场次行锁，再绑定行），避免交叉持锁导致数据库死锁。
     */
    @Transactional
    public EquipmentDispatchRecordDTO returnEquipment(Long sessionId, Long recordId, String operator) {
        Session session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessValidationException("场次不存在，ID: " + sessionId));

        EquipmentDispatchRecord record = dispatchRecordRepository.findByIdAndSessionId(recordId, sessionId)
                .orElseThrow(() -> new BusinessValidationException("发装流水不存在，ID: " + recordId));

        if (record.getStatus() == DispatchStatus.RETURNED) {
            throw new BusinessValidationException("该器材已于 " + record.getReturnTime() + " 归还，请勿重复归还");
        }
        if (record.getStatus() == DispatchStatus.AUTO_CLOSED) {
            throw new BusinessValidationException(
                    "场次「" + session.getSessionName() + "」已结束，该器材在结束时已由系统兜底收回，无需再归还");
        }

        int closed = dispatchRecordRepository.closeRecord(
                recordId, DispatchStatus.RETURNED, operator, LocalDateTime.now());
        if (closed == 0) {
            // 并发归还：closeRecord 带条件 status=ISSUED，只有一个请求能更新成功
            throw new ConflictException("该归还请求已被其他工作人员处理，本次操作未重复执行");
        }

        int bindingUpdated = sessionEquipmentRepository.markAvailableIfIssued(
                record.getSessionEquipmentId(), BindDispatchStatus.ISSUED, BindDispatchStatus.AVAILABLE);
        if (bindingUpdated == 0) {
            // 正常情况下与流水状态一致；不一致说明数据被异常改动，明确提示人工核查
            throw new ConflictException("器材绑定状态与流水状态不一致，请刷新后联系管理员核查");
        }

        EquipmentDispatchRecord refreshed = dispatchRecordRepository.findById(recordId).orElseThrow();
        Equipment equipment = equipmentRepository.findById(refreshed.getEquipmentId()).orElse(null);
        log.info("归还成功: 场次{} 流水{} 器材{} 操作员{}", sessionId, recordId,
                refreshed.getEquipmentId(), operator);
        return toRecordDTO(refreshed, equipment);
    }

    /**
     * 场次结束兜底：把所有"已领用未归还"的器材强制收回（AUTO_CLOSED），
     * 绑定状态复位为在架、资产状态复位为可用（未被其他场次绑定时），
     * 保证场次结束后不存在既不能重新领用/绑定、也无法归还的悬空器材。
     *
     * 由 SessionService 在持有场次行写锁的同一事务内调用。
     * @return 被兜底收回的器材数量
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int forceCloseOutstanding(Session session, String operator) {
        List<EquipmentDispatchRecord> outstanding =
                dispatchRecordRepository.findBySessionIdAndStatus(session.getId(), DispatchStatus.ISSUED);

        LocalDateTime now = LocalDateTime.now();
        for (EquipmentDispatchRecord record : outstanding) {
            int closed = dispatchRecordRepository.closeRecord(
                    record.getId(), DispatchStatus.AUTO_CLOSED,
                    operator != null ? operator : FALLBACK_OPERATOR, now);
            if (closed > 0) {
                sessionEquipmentRepository.markAvailableIfIssued(
                        record.getSessionEquipmentId(), BindDispatchStatus.ISSUED, BindDispatchStatus.AVAILABLE);
                resetEquipmentIfFree(record.getEquipmentId(), session.getId());
                log.warn("场次结束兜底收回: 场次{} 器材{} 原领用人{}({}) 发装时间{}",
                        session.getId(), record.getEquipmentId(),
                        record.getVisitorName(), record.getVisitorAgeGroup(), record.getIssueTime());
            }
        }
        return outstanding.size();
    }

    /** 现场视图：场次绑定器材 + 当前发装状态 + 当前未归还流水 */
    @Transactional(readOnly = true)
    public List<SessionDispatchItemDTO> listSessionItems(Long sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new BusinessValidationException("场次不存在，ID: " + sessionId);
        }
        List<SessionEquipment> bindings = sessionEquipmentRepository.findBySessionId(sessionId);
        List<Long> equipmentIds = bindings.stream().map(SessionEquipment::getEquipmentId).toList();

        Map<Long, Equipment> equipmentMap = equipmentRepository.findAllById(equipmentIds).stream()
                .collect(Collectors.toMap(Equipment::getId, Function.identity()));
        Map<Long, EquipmentDispatchRecord> activeMap = dispatchRecordRepository
                .findBySessionIdAndStatus(sessionId, DispatchStatus.ISSUED).stream()
                .collect(Collectors.toMap(EquipmentDispatchRecord::getEquipmentId, Function.identity()));

        List<SessionDispatchItemDTO> result = new ArrayList<>();
        for (SessionEquipment binding : bindings) {
            Equipment equipment = equipmentMap.get(binding.getEquipmentId());
            SessionDispatchItemDTO dto = new SessionDispatchItemDTO();
            dto.setSessionEquipmentId(binding.getId());
            dto.setEquipmentId(binding.getEquipmentId());
            dto.setTargetAgeGroup(binding.getTargetAgeGroup());
            dto.setTargetAgeGroupLabel(binding.getTargetAgeGroup().getLabel());
            BindDispatchStatus status = binding.getDispatchStatus() != null
                    ? binding.getDispatchStatus() : BindDispatchStatus.AVAILABLE;
            dto.setDispatchStatus(status);
            dto.setDispatchStatusLabel(status.getLabel());

            if (equipment != null) {
                dto.setEquipmentCode(equipment.getEquipmentCode());
                dto.setEquipmentName(equipment.getName());
                dto.setCategory(equipment.getCategory());
                dto.setFrostResistanceSpec(equipment.getFrostResistanceSpec());
                dto.setFrostLowerLimit(FrostSpecParser.parseLowerLimit(equipment.getFrostResistanceSpec()));
            }

            EquipmentDispatchRecord active = activeMap.get(binding.getEquipmentId());
            if (active != null) {
                dto.setActiveRecordId(active.getId());
                dto.setActiveVisitorName(active.getVisitorName());
                dto.setActiveVisitorAgeGroup(active.getVisitorAgeGroup());
                dto.setActiveVisitorAgeGroupLabel(active.getVisitorAgeGroup().getLabel());
                // 数据一致性兜底：若绑定状态因故没落为 ISSUED，以未归还流水为准
                dto.setDispatchStatus(BindDispatchStatus.ISSUED);
                dto.setDispatchStatusLabel(BindDispatchStatus.ISSUED.getLabel());
            }
            result.add(dto);
        }
        return result;
    }

    /** 按场次查发装/归还流水（发装时间倒序） */
    @Transactional(readOnly = true)
    public List<EquipmentDispatchRecordDTO> listRecords(Long sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new BusinessValidationException("场次不存在，ID: " + sessionId);
        }
        List<EquipmentDispatchRecord> records =
                dispatchRecordRepository.findBySessionIdOrderByIssueTimeDescIdDesc(sessionId);
        List<Long> equipmentIds = records.stream().map(EquipmentDispatchRecord::getEquipmentId).distinct().toList();
        Map<Long, Equipment> equipmentMap = equipmentIds.isEmpty()
                ? Map.of()
                : equipmentRepository.findAllById(equipmentIds).stream()
                        .collect(Collectors.toMap(Equipment::getId, Function.identity()));
        return records.stream()
                .map(r -> toRecordDTO(r, equipmentMap.get(r.getEquipmentId())))
                .toList();
    }

    /** 绑定/自动绑定时调用：器材有未归还发装时不允许再绑定到任何场次 */
    @Transactional(readOnly = true)
    public void assertEquipmentNotOutstanding(Long equipmentId) {
        if (dispatchRecordRepository.existsByEquipmentIdAndStatus(equipmentId, DispatchStatus.ISSUED)) {
            throw new ConflictException("该器材已在进行中的场次发给游客且尚未归还，不能绑定到新场次");
        }
    }

    /** 解绑时调用：本场次中该器材处于已领用状态时不允许解绑 */
    @Transactional(readOnly = true)
    public void assertNotOutstandingInSession(Long sessionId, Long equipmentId) {
        boolean outstanding = dispatchRecordRepository
                .findBySessionIdAndStatus(sessionId, DispatchStatus.ISSUED).stream()
                .anyMatch(r -> r.getEquipmentId().equals(equipmentId));
        if (outstanding) {
            throw new ConflictException("该器材已发给游客且尚未归还，无法解绑，请先完成归还或结束场次兜底收回");
        }
    }

    public boolean sessionHasOutstanding(Long sessionId) {
        return dispatchRecordRepository.existsBySessionIdAndStatus(sessionId, DispatchStatus.ISSUED);
    }

    /** 兜底收回后，器材若不再被其他场次绑定，资产状态复位为可用（可被新场次自动/手动绑定） */
    private void resetEquipmentIfFree(Long equipmentId, Long endedSessionId) {
        Equipment equipment = equipmentRepository.findById(equipmentId).orElse(null);
        if (equipment == null) {
            return;
        }
        boolean boundElsewhere = sessionEquipmentRepository.findByEquipmentId(equipmentId).stream()
                .anyMatch(se -> !se.getSessionId().equals(endedSessionId));
        if (!boundElsewhere) {
            equipment.setStatus(EquipmentStatus.AVAILABLE);
            equipmentRepository.save(equipment);
        }
    }

    private AgeGroup parseAgeGroup(String raw) {
        try {
            return AgeGroup.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            AgeGroup byLabel = AgeGroup.fromLabel(raw.trim());
            if (byLabel != null) {
                return byLabel;
            }
            throw new BusinessValidationException("游客年龄段不合法：" + raw
                    + "，应为 幼童/青少年/成人（CHILD/TEEN/ADULT）之一");
        }
    }

    private String outstandingKey(Long equipmentId) {
        return OUTSTANDING_KEY_PREFIX + equipmentId;
    }

    private EquipmentDispatchRecordDTO toRecordDTO(EquipmentDispatchRecord r, Equipment equipment) {
        EquipmentDispatchRecordDTO dto = new EquipmentDispatchRecordDTO();
        dto.setId(r.getId());
        dto.setSessionId(r.getSessionId());
        dto.setSessionEquipmentId(r.getSessionEquipmentId());
        dto.setEquipmentId(r.getEquipmentId());
        dto.setVisitorName(r.getVisitorName());
        dto.setVisitorAgeGroup(r.getVisitorAgeGroup());
        dto.setVisitorAgeGroupLabel(r.getVisitorAgeGroup().getLabel());
        dto.setTemperatureAtIssue(r.getTemperatureAtIssue());
        dto.setFrostLowerLimitAtIssue(r.getFrostLowerLimitAtIssue());
        dto.setIssueOperator(r.getIssueOperator());
        dto.setIssueTime(r.getIssueTime());
        dto.setStatus(r.getStatus());
        dto.setStatusLabel(r.getStatus().getLabel());
        dto.setReturnOperator(r.getReturnOperator());
        dto.setReturnTime(r.getReturnTime());
        if (equipment != null) {
            dto.setEquipmentCode(equipment.getEquipmentCode());
            dto.setEquipmentName(equipment.getName());
            dto.setFrostResistanceSpec(equipment.getFrostResistanceSpec());
        }
        return dto;
    }
}
