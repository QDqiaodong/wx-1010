package com.icepark;

import com.icepark.dto.EquipmentDispatchRecordDTO;
import com.icepark.dto.IssueRequestDTO;
import com.icepark.entity.Equipment;
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
import com.icepark.service.EquipmentDispatchService;
import com.icepark.service.SessionEquipmentService;
import com.icepark.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 发装台全链路验收测试：并发领用、临界气温、场次状态时序、结束兜底、归还再领用。
 * 使用 H2(MySQL 模式) 验证真实的数据库行锁/唯一索引/CAS 行为，不打桩。
 */
@SpringBootTest
class EquipmentDispatchIntegrationTest {

    @Autowired private EquipmentDispatchService dispatchService;
    @Autowired private SessionService sessionService;
    @Autowired private SessionEquipmentService sessionEquipmentService;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private SessionEquipmentRepository sessionEquipmentRepository;
    @Autowired private EquipmentDispatchRecordRepository dispatchRecordRepository;
    @Autowired private PlatformTransactionManager txManager;

    private Long sessionId;
    private Long equipmentId;
    private Long bindingId;

    @BeforeEach
    void setUp() {
        dispatchRecordRepository.deleteAll();
        sessionEquipmentRepository.deleteAll();
        equipmentRepository.deleteAll();
        sessionRepository.deleteAll();

        Session session = new Session();
        session.setSessionCode("S-" + System.nanoTime());
        session.setSessionName("测试场次");
        session.setStartTime(LocalDateTime.now().minusHours(1));
        session.setEndTime(LocalDateTime.now().plusHours(2));
        session.setChildRatio(BigDecimal.ZERO);
        session.setTeenRatio(BigDecimal.ZERO);
        session.setAdultRatio(BigDecimal.ONE);
        session.setStatus(SessionStatus.IN_PROGRESS);
        sessionId = sessionRepository.save(session).getId();

        Equipment equipment = new Equipment();
        equipment.setEquipmentCode("E-" + System.nanoTime());
        equipment.setName("极寒滑冰鞋");
        equipment.setFrostResistanceSpec("适用温度 -20℃ ~ 5℃");
        equipment.setAgeGroup(AgeGroup.ADULT);
        equipment.setCategory("冰面辅助器材");
        equipment.setStatus(EquipmentStatus.IN_USE);
        equipmentId = equipmentRepository.save(equipment).getId();

        SessionEquipment binding = new SessionEquipment();
        binding.setSessionId(sessionId);
        binding.setEquipmentId(equipmentId);
        binding.setTargetAgeGroup(AgeGroup.ADULT);
        binding.setDispatchStatus(BindDispatchStatus.AVAILABLE);
        bindingId = sessionEquipmentRepository.save(binding).getId();
    }

    private IssueRequestDTO issueReq(String visitorAgeGroup, String temperature) {
        IssueRequestDTO req = new IssueRequestDTO();
        req.setEquipmentId(equipmentId);
        req.setVisitorName("游客" + System.nanoTime());
        req.setVisitorAgeGroup(visitorAgeGroup);
        req.setTemperature(new BigDecimal(temperature));
        req.setOperator("工作人员A");
        return req;
    }

    // ---------- 约束一：并发领用，只有一次成功，其余明确"已被领用" ----------

    @Test
    void concurrentIssue_sameEquipment_onlyOneSucceeds() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        AtomicInteger otherError = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    EquipmentDispatchRecordDTO dto = dispatchService.issue(
                            sessionId, issueReq("ADULT", "-10"));
                    success.incrementAndGet();
                    assertNotNull(dto.getId());
                } catch (ConflictException e) {
                    conflict.incrementAndGet();
                    assertTrue(e.getMessage().contains("已被") || e.getMessage().contains("领用"),
                            "并发失败方必须明确收到已被领用，实际：" + e.getMessage());
                } catch (Exception e) {
                    otherError.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "并发任务未在限定时间内完成");
        pool.shutdown();

        assertEquals(1, success.get(), "同一件器材只能成功发出一次");
        assertEquals(threads - 1, conflict.get(), "其余请求都必须收到409已被领用");
        assertEquals(0, otherError.get(), "不应出现冲突以外的异常");
        assertEquals(1, dispatchRecordRepository.findBySessionIdAndStatus(sessionId, DispatchStatus.ISSUED).size());
        assertEquals(BindDispatchStatus.ISSUED,
                sessionEquipmentRepository.findById(bindingId).orElseThrow().getDispatchStatus());
    }

    // ---------- 约束二：年龄段 + 气温双校验，且能指出具体是哪一条 ----------

    @Test
    void issue_wrongAgeGroup_messageMentionsAgeMismatch() {
        BusinessValidationException ex = assertThrows(BusinessValidationException.class,
                () -> dispatchService.issue(sessionId, issueReq("CHILD", "-10")));
        assertTrue(ex.getMessage().contains("年龄段不匹配"), ex.getMessage());
        assertFalse(ex.getMessage().contains("气温不匹配"), "气温满足时不应提示气温问题");
    }

    @Test
    void issue_temperatureBelowLimit_messageMentionsTemperatureMismatch() {
        // 临界：下限 -20，-20.01 必须挡下
        BusinessValidationException ex = assertThrows(BusinessValidationException.class,
                () -> dispatchService.issue(sessionId, issueReq("ADULT", "-20.01")));
        assertTrue(ex.getMessage().contains("气温不匹配"), ex.getMessage());
        assertFalse(ex.getMessage().contains("年龄段不匹配"), "年龄段满足时不应提示年龄段问题");
    }

    @Test
    void issue_bothMismatch_messageMentionsBoth() {
        BusinessValidationException ex = assertThrows(BusinessValidationException.class,
                () -> dispatchService.issue(sessionId, issueReq("TEEN", "-25")));
        assertTrue(ex.getMessage().contains("年龄段不匹配"), ex.getMessage());
        assertTrue(ex.getMessage().contains("气温不匹配"), ex.getMessage());
    }

    @Test
    void issue_temperatureAtLowerLimit_isAllowed() {
        // 临界气温：实测气温恰好等于下限（-20℃），允许发装
        EquipmentDispatchRecordDTO dto =
                assertDoesNotThrow(() -> dispatchService.issue(sessionId, issueReq("ADULT", "-20")));
        assertEquals(DispatchStatus.ISSUED, dto.getStatus());
        assertEquals(0, new BigDecimal("-20").compareTo(dto.getFrostLowerLimitAtIssue()));
    }

    @Test
    void issue_visitorAgeGroupByChineseLabel_supported() {
        assertDoesNotThrow(() -> dispatchService.issue(sessionId, issueReq("成人", "-5")));
    }

    // ---------- 约束三：场次状态时序 ----------

    @Test
    void issue_whenSessionScheduled_isRejected() {
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            Session scheduled = sessionRepository.findById(sessionId).orElseThrow();
            scheduled.setStatus(SessionStatus.SCHEDULED);
        });
        BusinessValidationException ex = assertThrows(BusinessValidationException.class,
                () -> dispatchService.issue(sessionId, issueReq("ADULT", "-10")));
        assertTrue(ex.getMessage().contains("只有「进行中」"), ex.getMessage());
    }

    @Test
    void issue_whenSessionEnded_isRejected() {
        sessionService.endSession(sessionId);
        BusinessValidationException ex = assertThrows(BusinessValidationException.class,
                () -> dispatchService.issue(sessionId, issueReq("ADULT", "-10")));
        assertTrue(ex.getMessage().contains("只有「进行中」"), ex.getMessage());
    }

    // ---------- 约束三：场次结束兜底，无悬空器材 ----------

    @Test
    void endSession_forceClosesOutstandingRecords() {
        EquipmentDispatchRecordDTO issued = dispatchService.issue(sessionId, issueReq("ADULT", "-15"));

        com.icepark.dto.SessionDTO ended = sessionService.endSession(sessionId);
        assertEquals("ENDED", ended.getStatus());

        var record = dispatchRecordRepository.findById(issued.getId()).orElseThrow();
        assertEquals(DispatchStatus.AUTO_CLOSED, record.getStatus());
        assertNotNull(record.getReturnTime());
        assertEquals(BindDispatchStatus.AVAILABLE,
                sessionEquipmentRepository.findById(bindingId).orElseThrow().getDispatchStatus());
        // 资产复位可用，可被新场次绑定
        assertEquals(EquipmentStatus.AVAILABLE,
                equipmentRepository.findById(equipmentId).orElseThrow().getStatus());
        assertFalse(dispatchService.sessionHasOutstanding(sessionId));

        // 已兜底收回的流水不能再走正常归还（业务拒绝，明确提示已兜底收回）
        BusinessValidationException ex = assertThrows(BusinessValidationException.class,
                () -> dispatchService.returnEquipment(sessionId, issued.getId(), "工作人员B"));
        assertTrue(ex.getMessage().contains("兜底收回"), ex.getMessage());
    }

    @Test
    void endSession_thenRebindAndIssueInNewSession_works() {
        dispatchService.issue(sessionId, issueReq("ADULT", "-15"));
        sessionService.endSession(sessionId);

        // 新场次绑定同一件器材：应成功（旧场次已兜底收回，器材可用）
        Session s2 = new Session();
        s2.setSessionCode("S2-" + System.nanoTime());
        s2.setSessionName("第二场");
        s2.setStartTime(LocalDateTime.now());
        s2.setEndTime(LocalDateTime.now().plusHours(1));
        s2.setChildRatio(BigDecimal.ZERO);
        s2.setTeenRatio(BigDecimal.ZERO);
        s2.setAdultRatio(BigDecimal.ONE);
        s2.setStatus(SessionStatus.IN_PROGRESS);
        Long s2Id = sessionRepository.save(s2).getId();

        // 直接走绑定服务，验证 outstanding 守卫放行
        sessionEquipmentService.bindEquipment(s2Id, equipmentId, "ADULT");
        IssueRequestDTO req = issueReq("ADULT", "-10");
        req.setEquipmentId(equipmentId);
        EquipmentDispatchRecordDTO dto = dispatchService.issue(s2Id, req);
        assertEquals(DispatchStatus.ISSUED, dto.getStatus());
    }

    @Test
    void bindEquipment_whileOutstandingInOtherSession_isRejected() {
        dispatchService.issue(sessionId, issueReq("ADULT", "-15"));

        Session s2 = new Session();
        s2.setSessionCode("S2-" + System.nanoTime());
        s2.setSessionName("平行场次");
        s2.setStartTime(LocalDateTime.now());
        s2.setEndTime(LocalDateTime.now().plusHours(1));
        s2.setChildRatio(BigDecimal.ZERO);
        s2.setTeenRatio(BigDecimal.ZERO);
        s2.setAdultRatio(BigDecimal.ONE);
        s2.setStatus(SessionStatus.IN_PROGRESS);
        Long s2Id = sessionRepository.save(s2).getId();

        ConflictException ex = assertThrows(ConflictException.class,
                () -> sessionEquipmentService.bindEquipment(s2Id, equipmentId, "ADULT"));
        assertTrue(ex.getMessage().contains("尚未归还"), ex.getMessage());
    }

    // ---------- 发装—归还—再领用闭环 + 流水 ----------

    @Test
    void issue_return_thenReissue_flowCompletesAndRecordsTraceable() {
        EquipmentDispatchRecordDTO first = dispatchService.issue(sessionId, issueReq("ADULT", "-8"));

        List<com.icepark.dto.SessionDispatchItemDTO> items = dispatchService.listSessionItems(sessionId);
        assertEquals(BindDispatchStatus.ISSUED, items.get(0).getDispatchStatus());
        assertEquals(first.getId(), items.get(0).getActiveRecordId());

        EquipmentDispatchRecordDTO returned =
                dispatchService.returnEquipment(sessionId, first.getId(), "工作人员B");
        assertEquals(DispatchStatus.RETURNED, returned.getStatus());
        assertEquals("工作人员B", returned.getReturnOperator());
        assertNotNull(returned.getReturnTime());

        // 归还后器材立刻可重新领用
        EquipmentDispatchRecordDTO second = dispatchService.issue(sessionId, issueReq("ADULT", "-9"));
        assertNotEquals(first.getId(), second.getId());

        // 重复归还被拒绝
        assertThrows(BusinessValidationException.class,
                () -> dispatchService.returnEquipment(sessionId, first.getId(), "工作人员B"));

        // 流水按场次可查，两次发出 + 第一次归还痕迹完整
        List<EquipmentDispatchRecordDTO> records = dispatchService.listRecords(sessionId);
        assertEquals(2, records.size());
        EquipmentDispatchRecordDTO recordFirst = records.stream()
                .filter(r -> r.getId().equals(first.getId())).findFirst().orElseThrow();
        assertEquals("工作人员A", recordFirst.getIssueOperator());
        assertEquals("工作人员B", recordFirst.getReturnOperator());
        assertEquals("已归还", recordFirst.getStatusLabel());
        assertEquals("已领用",
                records.stream().filter(r -> r.getId().equals(second.getId())).findFirst()
                        .orElseThrow().getStatusLabel());
    }

    @Test
    void concurrentReturn_onlyOneSucceeds() throws Exception {
        EquipmentDispatchRecordDTO issued = dispatchService.issue(sessionId, issueReq("ADULT", "-8"));

        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    dispatchService.returnEquipment(sessionId, issued.getId(), "归还员");
                    success.incrementAndGet();
                } catch (Exception e) {
                    rejected.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(1, success.get());
        assertEquals(threads - 1, rejected.get());
        assertEquals(DispatchStatus.RETURNED,
                dispatchRecordRepository.findById(issued.getId()).orElseThrow().getStatus());
        assertEquals(BindDispatchStatus.AVAILABLE,
                sessionEquipmentRepository.findById(bindingId).orElseThrow().getDispatchStatus());
    }

    @Test
    void issue_unboundEquipment_isRejected() {
        BusinessValidationException ex = assertThrows(BusinessValidationException.class, () -> {
            IssueRequestDTO req = issueReq("ADULT", "-10");
            req.setEquipmentId(999999L);
            dispatchService.issue(sessionId, req);
        });
        assertTrue(ex.getMessage().contains("未绑定") || ex.getMessage().contains("器材不存在"));
    }
}
