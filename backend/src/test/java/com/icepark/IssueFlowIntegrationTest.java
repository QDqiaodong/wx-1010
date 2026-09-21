package com.icepark;

import com.icepark.dto.IssueRequestDTO;
import com.icepark.entity.Equipment;
import com.icepark.entity.IssueRecord;
import com.icepark.entity.Session;
import com.icepark.entity.SessionEquipment;
import com.icepark.enums.AgeGroup;
import com.icepark.enums.EquipmentStatus;
import com.icepark.enums.IssueStatus;
import com.icepark.enums.SessionStatus;
import com.icepark.exception.ConflictException;
import com.icepark.repository.EquipmentRepository;
import com.icepark.repository.IssueRecordRepository;
import com.icepark.repository.SessionEquipmentRepository;
import com.icepark.repository.SessionRepository;
import com.icepark.service.IssueService;
import com.icepark.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IssueFlowIntegrationTest {

    @Autowired private IssueService issueService;
    @Autowired private SessionService sessionService;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private SessionEquipmentRepository sessionEquipmentRepository;
    @Autowired private EquipmentRepository equipmentRepository;
    @Autowired private IssueRecordRepository issueRecordRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;

    private static final BigDecimal LIMIT = new BigDecimal("-20");

    private Session newSession(String code) {
        Session s = new Session();
        s.setSessionCode(code);
        s.setSessionName("场次-" + code);
        s.setStartTime(LocalDateTime.now().minusMinutes(10));
        s.setEndTime(LocalDateTime.now().plusHours(1));
        s.setChildRatio(new BigDecimal("0.4"));
        s.setTeenRatio(new BigDecimal("0.3"));
        s.setAdultRatio(new BigDecimal("0.3"));
        s.setStatus(SessionStatus.SCHEDULED);
        return sessionRepository.save(s);
    }

    private Equipment newEquipment(String code, AgeGroup age, BigDecimal minTemp) {
        Equipment e = new Equipment();
        e.setEquipmentCode(code);
        e.setName("器材-" + code);
        e.setFrostResistanceSpec("抗寒下限" + minTemp + "℃");
        e.setMinTemperature(minTemp);
        e.setAgeGroup(age);
        e.setCategory("其他");
        e.setStatus(EquipmentStatus.AVAILABLE);
        return equipmentRepository.save(e);
    }

    private SessionEquipment bind(Long sessionId, Equipment e, AgeGroup target) {
        SessionEquipment se = new SessionEquipment();
        se.setSessionId(sessionId);
        se.setEquipmentId(e.getId());
        se.setTargetAgeGroup(target);
        return sessionEquipmentRepository.save(se);
    }

    private IssueRequestDTO req(Long seId, AgeGroup age, BigDecimal temp, String visitor) {
        IssueRequestDTO r = new IssueRequestDTO();
        r.setSessionEquipmentId(seId);
        r.setVisitorAgeGroup(age.name());
        r.setMeasuredTemperature(temp);
        r.setVisitorId(visitor);
        r.setVisitorName("游客" + visitor);
        r.setOperator("staff-" + visitor);
        return r;
    }

    // ----------------------------------------------------------------
    // 1. 并发领用：同一件器材只能成功发出一次，其余全部收到“已被领用”
    // ----------------------------------------------------------------
    @Test
    void concurrentIssue_onlyOneWins() throws Exception {
        Session s = newSession("S-CONC");
        sessionService.startSession(s.getId());
        Equipment e = newEquipment("E-CONC", AgeGroup.CHILD, LIMIT);
        SessionEquipment se = bind(s.getId(), e, AgeGroup.CHILD);

        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        List<Throwable> others = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    issueService.issue(s.getId(), req(se.getId(), AgeGroup.CHILD, new BigDecimal("-15"), "V" + idx));
                    success.incrementAndGet();
                } catch (ConflictException ce) {
                    if (ce.getMessage().contains("已被领用") || ce.getMessage().contains("领用")) {
                        conflict.incrementAndGet();
                    } else {
                        synchronized (others) { others.add(ce); }
                    }
                } catch (Throwable t) {
                    synchronized (others) { others.add(t); }
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));

        assertEquals(1, success.get(), "并发下必须恰好成功一次");
        assertEquals(threads - 1, conflict.get(), "其余请求必须明确收到已被领用");
        assertTrue(others.isEmpty(), "不应出现其它异常: " + others);

        Equipment reloaded = equipmentRepository.findById(e.getId()).orElseThrow();
        assertNotNull(reloaded.getCurrentIssueRecordId(), "器材应被唯一流水占用");
        assertEquals(EquipmentStatus.IN_USE, reloaded.getStatus());

        long openCount = issueRecordRepository.findBySessionIdOrderByIssueTimeDesc(s.getId()).stream()
                .filter(r -> r.getStatus() == IssueStatus.ISSUED).count();
        assertEquals(1, openCount, "失败的流水必须随事务回滚，只剩 1 条在用流水");
    }

    // ----------------------------------------------------------------
    // 2. 临界气温：等于下限可用，低于下限拒绝；与年龄段校验互不遮蔽
    // ----------------------------------------------------------------
    @Test
    void boundaryTemperature_andDoubleValidation() {
        Session s = newSession("S-TEMP");
        sessionService.startSession(s.getId());

        // 2.1 临界：-20 恰好等于下限，放行
        Equipment e1 = newEquipment("E-T1", AgeGroup.CHILD, LIMIT);
        SessionEquipment se1 = bind(s.getId(), e1, AgeGroup.CHILD);
        assertDoesNotThrow(() ->
                issueService.issue(s.getId(), req(se1.getId(), AgeGroup.CHILD, new BigDecimal("-20"), "T1")));

        // 2.2 低于下限 0.1℃：只报气温
        Equipment e2 = newEquipment("E-T2", AgeGroup.CHILD, LIMIT);
        SessionEquipment se2 = bind(s.getId(), e2, AgeGroup.CHILD);
        RuntimeException ex2 = assertThrows(RuntimeException.class, () ->
                issueService.issue(s.getId(), req(se2.getId(), AgeGroup.CHILD, new BigDecimal("-20.1"), "T2")));
        assertTrue(ex2.getMessage().contains("气温不匹配"), ex2.getMessage());
        assertFalse(ex2.getMessage().contains("年龄段不匹配"), "年龄匹配时不应报年龄段");

        // 2.3 年龄不匹配但气温达标：只报年龄
        Equipment e3 = newEquipment("E-T3", AgeGroup.ADULT, LIMIT);
        SessionEquipment se3 = bind(s.getId(), e3, AgeGroup.ADULT);
        RuntimeException ex3 = assertThrows(RuntimeException.class, () ->
                issueService.issue(s.getId(), req(se3.getId(), AgeGroup.CHILD, new BigDecimal("-10"), "T3")));
        assertTrue(ex3.getMessage().contains("年龄段不匹配"), ex3.getMessage());
        assertFalse(ex3.getMessage().contains("气温不匹配"), "气温达标时不应报气温");

        // 2.4 两条同时不满足：两条都要报
        Equipment e4 = newEquipment("E-T4", AgeGroup.ADULT, LIMIT);
        SessionEquipment se4 = bind(s.getId(), e4, AgeGroup.ADULT);
        RuntimeException ex4 = assertThrows(RuntimeException.class, () ->
                issueService.issue(s.getId(), req(se4.getId(), AgeGroup.CHILD, new BigDecimal("-30"), "T4")));
        assertTrue(ex4.getMessage().contains("年龄段不匹配"), ex4.getMessage());
        assertTrue(ex4.getMessage().contains("气温不匹配"), ex4.getMessage());
    }

    // ----------------------------------------------------------------
    // 3. 场次时序 + 结束兜底，杜绝悬空
    // ----------------------------------------------------------------
    @Test
    void sessionTiming_andForceCloseOnEnd() {
        // 3.1 未开始不允许发装
        Session s0 = newSession("S-NOTSTART");
        Equipment e0 = newEquipment("E-NS", AgeGroup.CHILD, LIMIT);
        SessionEquipment se0 = bind(s0.getId(), e0, AgeGroup.CHILD);
        RuntimeException notStarted = assertThrows(RuntimeException.class, () ->
                issueService.issue(s0.getId(), req(se0.getId(), AgeGroup.CHILD, new BigDecimal("-15"), "X")));
        assertTrue(notStarted.getMessage().contains("尚未开始"), notStarted.getMessage());

        // 3.2 进行中发装，中途结束触发兜底
        Session s = newSession("S-END");
        sessionService.startSession(s.getId());
        Equipment e = newEquipment("E-END", AgeGroup.CHILD, LIMIT);
        SessionEquipment se = bind(s.getId(), e, AgeGroup.CHILD);
        Long issueId = issueService.issue(s.getId(), req(se.getId(), AgeGroup.CHILD, new BigDecimal("-15"), "Z")).getId();

        Equipment occupied = equipmentRepository.findById(e.getId()).orElseThrow();
        assertEquals(EquipmentStatus.IN_USE, occupied.getStatus());
        assertNotNull(occupied.getCurrentIssueRecordId());

        sessionService.endSession(s.getId(), "manager");

        // 场次已结束
        assertEquals(SessionStatus.ENDED, sessionRepository.findById(s.getId()).orElseThrow().getStatus());

        // 流水被兜底归还，留痕完整
        IssueRecord rec = issueRecordRepository.findById(issueId).orElseThrow();
        assertEquals(IssueStatus.FORCE_CLOSED, rec.getStatus());
        assertNotNull(rec.getReturnTime());
        assertEquals("manager", rec.getReturnOperator());
        assertNotNull(rec.getCloseReason());

        // 器材占用被释放，可被新场次绑定/再次领用 —— 不存在悬空
        Equipment freed = equipmentRepository.findById(e.getId()).orElseThrow();
        assertNull(freed.getCurrentIssueRecordId());
        assertEquals(EquipmentStatus.AVAILABLE, freed.getStatus());

        // 已结束场次不能再发装
        RuntimeException endedIssue = assertThrows(RuntimeException.class, () ->
                issueService.issue(s.getId(), req(se.getId(), AgeGroup.CHILD, new BigDecimal("-15"), "Y")));
        assertTrue(endedIssue.getMessage().contains("已结束"), endedIssue.getMessage());

        // 被兜底的流水不能再走“归还”
        RuntimeException endedReturn = assertThrows(RuntimeException.class, () ->
                issueService.returnIssue(s.getId(), issueId, "staff"));
        assertNotNull(endedReturn);

        // 释放出的器材可绑定到一个新的进行中场次并成功发装
        Session s2 = newSession("S-REUSE");
        sessionService.startSession(s2.getId());
        SessionEquipment se2 = bind(s2.getId(), e, AgeGroup.CHILD);
        Long id2 = issueService.issue(s2.getId(), req(se2.getId(), AgeGroup.CHILD, new BigDecimal("-12"), "W")).getId();
        assertNotNull(id2);
    }

    // ----------------------------------------------------------------
    // 4. 正常归还后器材可再次领用；流水可查
    // ----------------------------------------------------------------
    @Test
    void returnThenReissue_andRecords() {
        Session s = newSession("S-RET");
        sessionService.startSession(s.getId());
        Equipment e = newEquipment("E-RET", AgeGroup.TEEN, LIMIT);
        SessionEquipment se = bind(s.getId(), e, AgeGroup.TEEN);

        Long id1 = issueService.issue(s.getId(), req(se.getId(), AgeGroup.TEEN, new BigDecimal("-10"), "R1")).getId();
        issueService.returnIssue(s.getId(), id1, "returner");

        IssueRecord r1 = issueRecordRepository.findById(id1).orElseThrow();
        assertEquals(IssueStatus.RETURNED, r1.getStatus());
        assertNotNull(r1.getReturnTime());
        assertEquals(EquipmentStatus.AVAILABLE, equipmentRepository.findById(e.getId()).orElseThrow().getStatus());
        assertNull(equipmentRepository.findById(e.getId()).orElseThrow().getCurrentIssueRecordId());

        // 重复归还被挡
        assertThrows(ConflictException.class, () -> issueService.returnIssue(s.getId(), id1, "returner"));

        // 再次领用成功（同一件器材第二轮）
        Long id2 = issueService.issue(s.getId(), req(se.getId(), AgeGroup.TEEN, new BigDecimal("-10"), "R2")).getId();
        assertNotEquals(id1, id2);
        issueService.returnIssue(s.getId(), id2, "returner2");

        // 按场次可查整条流水，且含经办人/时间/状态
        var dtos = issueService.getRecordsBySession(s.getId());
        assertEquals(2, dtos.size());
        for (var d : dtos) {
            assertEquals(IssueStatus.RETURNED.name(), d.getStatus().name());
            assertNotNull(d.getIssueTime());
            assertNotNull(d.getReturnTime());
            assertNotNull(d.getIssueOperator());
            assertNotNull(d.getReturnOperator());
            assertNotNull(d.getEquipmentCode());
            assertNotNull(d.getVisitorId());
        }
    }

    // ----------------------------------------------------------------
    // 5. 直接验证占用锚点本身的原子性（跨线程 claim 同一件器材）
    // ----------------------------------------------------------------
    @Test
    void claimAnchorIsAtomic() throws Exception {
        Equipment e = newEquipment("E-ANCHOR", AgeGroup.ADULT, LIMIT);
        IssueRecord r1 = new IssueRecord();
        r1.setSessionId(901L);
        r1.setSessionEquipmentId(9001L);
        r1.setEquipmentId(e.getId());
        r1.setVisitorId("A1");
        r1.setVisitorAgeGroup(AgeGroup.ADULT);
        r1.setMeasuredTemperature(new BigDecimal("-10"));
        r1.setLimitTemperature(LIMIT);
        r1.setStatus(IssueStatus.ISSUED);
        r1.setIssueOperator("o");
        r1.setIssueTime(LocalDateTime.now());
        r1 = issueRecordRepository.save(r1);

        IssueRecord r2 = new IssueRecord();
        r2.setSessionId(902L);
        r2.setSessionEquipmentId(9002L);
        r2.setEquipmentId(e.getId());
        r2.setVisitorId("A2");
        r2.setVisitorAgeGroup(AgeGroup.ADULT);
        r2.setMeasuredTemperature(new BigDecimal("-10"));
        r2.setLimitTemperature(LIMIT);
        r2.setStatus(IssueStatus.ISSUED);
        r2.setIssueOperator("o");
        r2.setIssueTime(LocalDateTime.now());
        r2 = issueRecordRepository.save(r2);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            final Long issueId = (i % 2 == 0) ? r1.getId() : r2.getId();
            pool.submit(() -> {
                try {
                    start.await();
                    Integer n = transactionTemplate.execute(
                            st -> equipmentRepository.claim(e.getId(), issueId, LocalDateTime.now()));
                    wins.addAndGet(n == null ? 0 : n);
                } catch (Throwable ignored) {
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(1, wins.get(), "原子抢占必须只命中一次");
    }
}
